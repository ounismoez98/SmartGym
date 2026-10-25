import { createServer } from 'node:http';

const server = createServer((request, response) => {
  const chunks = [];
  request.on('data', (chunk) => chunks.push(chunk));
  request.on('end', () => {
    try {
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
      const isJson = body.response_format?.type === 'json_object';
      let content = 'Mock assistant response based only on supplied test facts.';
      if (isJson) {
        const facts = JSON.parse(body.messages.find((message) => message.role === 'user').content);
        content = JSON.stringify({
          subject: `Renewal for ${facts.planName}`,
          body: `Hello ${facts.memberFirstName}, your subscription expires on ${facts.expirationDate}. Amount due: ${facts.amount}.`
        });
      }
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ choices: [{ message: { content } }] }));
    } catch {
      response.writeHead(400, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ error: 'Invalid mock request' }));
    }
  });
});

server.listen(4010, '0.0.0.0');

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
