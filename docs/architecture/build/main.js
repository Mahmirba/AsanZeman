const { build } = require('./lib');
const blocks = [...require('./c1')(), ...require('./c2')(), ...require('./c3')()];
build(blocks, {
  version: 'نسخه ۲.۰', versionShort: 'نسخه ۲.۰', date: 'تاریخ: ۱۴۰۵/۰۸/۲۳',
  basis: 'مبنا: نسخه ۱.۰ این سند، SRS-AZS نسخه ۱.۰ و تصمیمات D1 تا D40',
  numberLists: ['lockLayers', 'ncode', 'issueFlow', 'cicd'],
}, process.argv[2] || require('path').join(__dirname, '../AsanZeman_Architecture_Document_v2.0.docx')).then(() => console.log('ok')).catch((e) => { console.error(e); process.exit(1); });
