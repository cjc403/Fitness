import { readFile, lstat } from 'node:fs/promises';
import path from 'node:path';
import { createServer } from './server.mjs';

try{
 const args=process.argv.slice(2);if(args.length!==2||args[0]!=='--config'||!path.isAbsolute(args[1]))throw new Error('Use --config <absolute protected bridge.json path>');
 const configPath=args[1];if((await lstat(configPath)).isSymbolicLink())throw new Error('Private bridge config must not be a link');
 const privateConfig=JSON.parse(await readFile(configPath,'utf8'));
 if(typeof privateConfig.token!=='string'||privateConfig.token.length<32)throw new Error('Private bridge token is missing');
 const config={...privateConfig,bridgeToken:privateConfig.token,host:'127.0.0.1',frontendOrigin:privateConfig.frontendOrigin||'http://localhost:5173',backendOrigin:privateConfig.backendOrigin||'http://localhost:8080',outputDirectory:privateConfig.outputDirectory||path.join(process.env.USERPROFILE,'Desktop','FitChain任务成果'),executablePath:privateConfig.executablePath||'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'};
 const server=createServer(config);
 server.on('error',()=>{process.stderr.write('FitChain电脑执行器启动失败；请检查端口和私有配置。\n');process.exitCode=1;});
 server.listen(config.port||5188,'127.0.0.1',()=>process.stdout.write('FitChain电脑执行器已启动（仅本机）。\n'));
 const shutdown=async()=>{await server.closeAgent();server.close(()=>process.exit());};process.once('SIGINT',shutdown);process.once('SIGTERM',shutdown);
}catch{process.stderr.write('FitChain电脑执行器配置无效；请通过项目启动脚本启动。\n');process.exitCode=1;}
