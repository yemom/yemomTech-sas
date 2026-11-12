const { Client } = require('pg');
const client = new Client({
    host: '127.0.0.1',
    port: 5632,
    user: 'YemomBizGrow_user',
    password: 'YemomBizGrow_password',
    database: 'YemomBizGrow'
});
client.connect((err) => {
    if (err) {
        console.error('Connection failed:', err.message);
        process.exit(1);
    } else {
        console.log('SUCCESSFULLY CONNECTED FROM HOST!');
        client.end();
        process.exit(0);
    }
});
