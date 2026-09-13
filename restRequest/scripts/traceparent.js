function randomHex(length) {
    let result = '';
    const chars = '0123456789abcdef';
    for (let i = 0; i < length; i++) {
        result += chars[Math.floor(Math.random() * chars.length)];
    }
    return result;
}

const traceId = randomHex(32);
const spanId = randomHex(16);

request.variables.set("traceId", traceId);
request.variables.set("spanId", spanId);
