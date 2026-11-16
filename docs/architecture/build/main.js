const { build } = require('./lib');
const blocks = [...require('./c1')(), ...require('./c2')(), ...require('./c3')()];
build(blocks, {
  version: 'نسخه ۱.۰', versionShort: 'نسخه ۱.۰', date: 'تاریخ: ۱۴۰۵/۰۸/۲۵',
  basis: 'مبنا: SRS-AZS نسخه ۱.۰، مستندات سرویس‌های بانک و تصمیمات D1 تا D47',
  numberLists: ['lockLayers', 'ncode', 'issueFlow', 'cicd'],
}, process.argv[2] || require('path').join(__dirname, '../AsanZeman_Architecture_Document_v1.0.docx')).then(() => console.log('ok')).catch((e) => { console.error(e); process.exit(1); });
