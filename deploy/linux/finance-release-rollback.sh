#!/usr/bin/env bash
# Sourced by release-refund-finance.sh. Functions are overridable for controlled tests.

finance_cleanup_step() {
  local label=$1
  shift
  if ! "$@"; then
    echo "secondary rollback failure: $label" >&2
    rollback_clean=false
  fi
}

establish_maintenance_gate() {
  if [[ -e "$MAINTENANCE_FLAG" ]]; then return 0; fi
  if (set -o noclobber; : > "$MAINTENANCE_FLAG") 2>/dev/null; then
    marker_owned=true
    chmod 0644 "$MAINTENANCE_FLAG"
    return 0
  fi
  # During rollback, a racing operator marker is an effective gate, but not ours.
  [[ -f "$MAINTENANCE_FLAG" ]]
}

establish_initial_maintenance_gate() {
  gate_owned_by_release=true
  establish_maintenance_gate || return $?
  if [[ ${marker_owned:-false} != true ]]; then
    echo 'unowned maintenance marker appeared; refusing release' >&2
    return 1
  fi
}

remove_owned_maintenance_gate() {
  if [[ ${marker_owned:-false} != true ]]; then
    echo 'refusing to remove unowned maintenance marker' >&2
    return 1
  fi
  rm -f -- "$MAINTENANCE_FLAG" || return $?
  marker_owned=false
}

stop_app_for_release() {
  # The obligation exists before stop begins: stop may terminate the app yet fail,
  # and a signal may run the trap at any point inside stop_application.
  app_recovery_required=true
  stop_application
}

finance_verify_old_app() {
  local label=$1
  if verify_old_app; then
    old_app_verified=true
  else
    echo "secondary rollback failure: $label" >&2
    rollback_clean=false
  fi
}

finance_rollback() {
  local primary_status=${1:-1}
  trap - ERR INT TERM
  set +e
  rollback_clean=true
  old_app_verified=true
  if [[ ${swapped:-false} == true ]]; then
    echo 'Finance release failed; attempting every previous-application restore step' >&2
    old_app_verified=false
    if [[ ${gate_owned_by_release:-false} == true ]]; then
      finance_cleanup_step 're-establish maintenance gate' establish_maintenance_gate
      finance_cleanup_step 'verify maintenance gate' verify_maintenance_gate
    fi
    finance_cleanup_step 'restore backend' restore_backend
    finance_cleanup_step 'restore frontend link' restore_frontend
    finance_cleanup_step 'restart previous application' restart_old_app
    finance_verify_old_app 'verify previous application'
  elif [[ ${app_recovery_required:-false} == true ]]; then
    old_app_verified=false
    finance_cleanup_step 'restart quiesced application' restart_old_app
    finance_verify_old_app 'verify quiesced application'
  fi
  finance_cleanup_step 'restore watchdog state' restore_watchdog
  if [[ $rollback_clean == true && $old_app_verified == true && ${marker_owned:-false} == true ]]; then
    finance_cleanup_step 'remove owned maintenance marker' remove_owned_maintenance_gate
  fi
  exit "$primary_status"
}
