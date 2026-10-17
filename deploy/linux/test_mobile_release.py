import os, pathlib, subprocess, tempfile, unittest

ROOT=pathlib.Path(__file__).parent
BASH=r'C:/Program Files/Git/bin/bash.exe' if os.name=='nt' else 'bash'

class MobileReleaseTest(unittest.TestCase):
    def case(self, failure):
        with tempfile.TemporaryDirectory() as folder:
            p=pathlib.Path(folder)
            for name in ['upload','backup','old-front','new-front']:(p/name).mkdir()
            (p/'live.jar').write_text('old-jar');(p/'live.env').write_text('old-env');(p/'nginx.conf').write_text('old-nginx')
            (p/'running.jar').write_text('old-jar');(p/'running.env').write_text('old-env');(p/'active').write_text('yes')
            for name,body in [('fitness-backend.jar','new-jar'),('candidate.env','new-env'),('fitchain-nginx.conf','new-nginx')]: (p/'upload'/name).write_text(body)
            script='''set -Eeuo pipefail
export FITCHAIN_RELEASE_LIBRARY_ONLY=1
source "$1"
BASE="$2"; UPLOAD="$BASE/upload"; BACKUP="$BASE/backup"; FRONT="$BASE/new-front"; OLD_FRONT="$BASE/old-front"
CURRENT="$BASE/current"; LIVE_JAR="$BASE/live.jar"; ENV_FILE="$BASE/live.env"; NGINX_FILE="$BASE/nginx.conf"; MAINTENANCE_FLAG="$BASE/maintenance"
LOG="$BASE/log"; FAILURE="$3"; printf '%s' "$OLD_FRONT" >"$CURRENT"
verify_upload(){ return 0; }
verify_maintenance_gate(){ return 0; }
systemctl(){
 echo "$*" >>"$LOG"
 if [[ $* == 'stop fitchain' ]]; then printf no >"$BASE/active"; fi
 if [[ $* == 'restart fitchain' || $* == 'start fitchain' && $(cat "$BASE/active") != yes ]]; then cp "$LIVE_JAR" "$BASE/running.jar"; cp "$ENV_FILE" "$BASE/running.env"; printf yes >"$BASE/active"; fi
 if [[ $* == 'reload nginx' && $FAILURE == reload ]]; then return 1; fi
 return 0
}
stop_application(){ echo stop >>"$LOG"; systemctl stop fitchain; [[ $FAILURE != stop ]]; }
place_file(){ cp "$1" "$2"; }
switch_frontend(){ printf '%s' "$1" >"$CURRENT"; }
create_database_backup(){ echo dump >>"$LOG"; [[ $FAILURE != backup ]]; }
snapshot_database(){ echo '{}' >"$1"; }
install_candidate(){ place_file "$UPLOAD/fitness-backend.jar" "$LIVE_JAR"; place_file "$UPLOAD/candidate.env" "$ENV_FILE"; place_file "$UPLOAD/fitchain-nginx.conf" "$NGINX_FILE"; switch_frontend "$FRONT"; }
verify_candidate(){ [[ $FAILURE != verify && $FAILURE != nginx-test && $FAILURE != reload && $FAILURE != rollback ]]; }
verify_old_app(){ echo old-verified >>"$LOG"; [[ $FAILURE != rollback ]]; }
nginx(){ [[ $FAILURE != nginx-test ]]; }
perform_release
'''
            entry=p/'case.sh';entry.write_text(script,encoding='utf-8',newline='\n')
            result=subprocess.run([BASH,str(entry),str(ROOT/'release-mobile-v23.sh'),str(p),failure],capture_output=True,text=True)
            state={name:(p/name).read_text() for name in ['live.jar','live.env','nginx.conf']}
            self.assertEqual((p/'running.jar').read_text(),'new-jar' if failure=='success' else 'old-jar','Restoring files must restore the running process too')
            self.assertEqual((p/'running.env').read_text(),'new-env' if failure=='success' else 'old-env')
            expected=p/('new-front' if failure=='success' else 'old-front')
            self.assertEqual(pathlib.Path((p/'current').read_text()),expected)
            return result,state,(p/'maintenance').exists(),(p/'log').read_text() if (p/'log').exists() else ''

    def test_backup_failure_keeps_old_artifacts_and_restarts_quiesced_app(self):
        r,s,gate,log=self.case('backup');self.assertNotEqual(r.returncode,0);self.assertEqual(s,{'live.jar':'old-jar','live.env':'old-env','nginx.conf':'old-nginx'});self.assertIn('old-verified',log);self.assertFalse(gate)
    def test_post_cutover_failure_restores_backend_env_nginx_and_frontend(self):
        r,s,gate,log=self.case('verify');self.assertNotEqual(r.returncode,0);self.assertEqual(s,{'live.jar':'old-jar','live.env':'old-env','nginx.conf':'old-nginx'});self.assertIn('old-verified',log);self.assertFalse(gate)
    def test_failed_stop_still_recovers_the_old_app(self):
        r,s,gate,log=self.case('stop');self.assertNotEqual(r.returncode,0);self.assertIn('old-verified',log);self.assertFalse(gate)
    def test_unverified_rollback_keeps_maintenance_closed(self):
        r,s,gate,log=self.case('rollback');self.assertNotEqual(r.returncode,0);self.assertTrue(gate)
    def test_success_keeps_new_artifacts_and_reopens_traffic(self):
        r,s,gate,log=self.case('success');self.assertEqual(r.returncode,0,r.stderr);self.assertEqual(s,{'live.jar':'new-jar','live.env':'new-env','nginx.conf':'new-nginx'});self.assertFalse(gate)
    def test_nginx_validation_failure_keeps_maintenance_closed(self):
        r,s,gate,log=self.case('nginx-test');self.assertNotEqual(r.returncode,0);self.assertTrue(gate)
    def test_nginx_reload_failure_keeps_maintenance_closed(self):
        r,s,gate,log=self.case('reload');self.assertNotEqual(r.returncode,0);self.assertTrue(gate)
    def test_failed_old_health_is_not_hidden_by_correct_disk_hash(self):
        with tempfile.TemporaryDirectory() as folder:
            p=pathlib.Path(folder);(p/'old.jar').write_text('old')
            script='export FITCHAIN_RELEASE_LIBRARY_ONLY=1; source "$1"; LIVE_JAR="$2/old.jar"; OLD_SHA=$(sha256sum "$LIVE_JAR"|cut -d" " -f1); healthy(){ return 1; }; if verify_old_app; then exit 0; else exit 39; fi'
            r=subprocess.run([BASH,'-c',script,'test',str(ROOT/'release-mobile-v23.sh'),str(p)])
            self.assertEqual(r.returncode,39)

if __name__=='__main__':unittest.main()
