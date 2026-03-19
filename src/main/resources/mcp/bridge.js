#!/usr/bin/env node
// MCP stdio <-> TCP bridge
// kiro-cli가 이 스크립트를 MCP 서버로 실행하면,
// stdin/stdout(MCP stdio 프로토콜)을 IDE의 TCP 서버로 중계한다.

const net = require('net');
const readline = require('readline');

const port = parseInt(process.argv[2], 10);
if (!port) {
  process.stderr.write('Usage: bridge.js <port>\n');
  process.exit(1);
}

const client = net.createConnection({ port, host: '127.0.0.1' }, () => {
  process.stderr.write(`Connected to IDE on port ${port}\n`);
});

// stdin -> TCP (kiro-cli -> IDE)
const rl = readline.createInterface({ input: process.stdin, terminal: false });
rl.on('line', (line) => {
  client.write(line + '\n');
});

// TCP -> stdout (IDE -> kiro-cli)
const tcpRl = readline.createInterface({ input: client, terminal: false });
tcpRl.on('line', (line) => {
  process.stdout.write(line + '\n');
});

client.on('error', (err) => {
  process.stderr.write(`Connection error: ${err.message}\n`);
  process.exit(1);
});

client.on('close', () => {
  process.exit(0);
});

process.stdin.on('end', () => {
  client.end();
});
