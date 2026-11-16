// Appendix B: diagram sources (Mermaid / Graphviz), read from the repo at build time
const fs = require('fs');
const path = require('path');
const { P, H1, H2, CODE, T } = require('./lib');
const FA = (n) => String(n).replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
const D = path.join(__dirname, '../diagrams/');
const S = path.join(__dirname, '../sequence/');
const items = [
  ['شکل ۲-۱ نمای Context', 'diagrams/context.mmd', 'Mermaid flowchart', D + 'context.mmd'],
  ['شکل ۳-۱ نمای Container', 'diagrams/container.mmd', 'Mermaid flowchart', D + 'container.mmd'],
  ['شکل ۴-۱ نمای Component', 'diagrams/component.dot', 'Graphviz', D + 'component.dot'],
  ['شکل ۴-۲ ورود کاربر بانک', 'sequence/s01_bank_login.mmd', 'Mermaid sequence', S + 's01_bank_login.mmd'],
  ['شکل ۴-۳ ورود کاربر آژانس', 'sequence/s02_agency_login.mmd', 'Mermaid sequence', S + 's02_agency_login.mmd'],
  ['شکل ۴-۴ ثبت درخواست توسط آژانس', 'sequence/s03_registration.mmd', 'Mermaid sequence', S + 's03_registration.mmd'],
  ['شکل ۴-۵ ورود مشتری با OTP', 'sequence/s04_customer_login.mmd', 'Mermaid sequence', S + 's04_customer_login.mmd'],
  ['شکل ۴-۶ اجرای استعلام‌ها', 'sequence/s05_inquiries.mmd', 'Mermaid sequence', S + 's05_inquiries.mmd'],
  ['شکل ۴-۷ انتخاب وثیقه و ثبت نهایی', 'sequence/s06_collateral.mmd', 'Mermaid sequence', S + 's06_collateral.mmd'],
  ['شکل ۴-۸ بررسی شعبه و صدور دستی', 'sequence/s07_branch_issue.mmd', 'Mermaid sequence', S + 's07_branch_issue.mmd'],
  ['شکل ۴-۹ لغو یا ابطال توسط آژانس', 'sequence/s08_cancel_revoke.mmd', 'Mermaid sequence', S + 's08_cancel_revoke.mmd'],
  ['شکل ۴-۱۰ ارسال پیامک از Outbox', 'sequence/s09_outbox.mmd', 'Mermaid sequence', S + 's09_outbox.mmd'],
  ['شکل ۴-۱۱ نشست یاقوت و توکن Gateway', 'sequence/s10_sessions.mmd', 'Mermaid sequence', S + 's10_sessions.mmd'],
  ['شکل ۵-۱ و ۵-۲ ERD', 'diagrams/erd_gen.py', 'Python → Graphviz', D + 'erd_gen.py'],
  ['شکل ۸-۱ دیاگرام استقرار', 'diagrams/deployment.mmd', 'Mermaid flowchart', D + 'deployment.mmd'],
];
module.exports = () => [
  H1('پیوست ب — منبع نمودارها'),
  P('همه نمودارهای این سند از متن تولید می‌شوند و منبع آن‌ها در مخزن پروژه زیر `docs/architecture/` نگهداری می‌شود. نمودارهای Mermaid را می‌توان مستقیماً در `mermaid.live` یا در یک بلوک `mermaid` در Markdown (که GitHub آن را رندر می‌کند) باز کرد؛ نمودارهای توالی با فایل تنظیمات `sequence/mermaid-config.json` رنگ‌آمیزی می‌شوند. نمودارهای Component و ERD با Graphviz ساخته می‌شوند و ERD از اسکریپت `erd_gen.py` تولید می‌شود تا افزودن یک ستون یک خط تغییر باشد. دستورهای رندر در `docs/architecture/README.md` آمده است.'),
  CODE(`mmdc -i diagrams/context.mmd -o context.png -w 1400 -s 2 -b white
mmdc -i sequence/s05_inquiries.mmd -o s05.png -c sequence/mermaid-config.json -w 1300 -s 2 -b white
dot  -Tpng -Gdpi=200 diagrams/component.dot -o component.png
python3 diagrams/erd_gen.py && dot -Tpng -Gdpi=220 diagrams/erd_business.dot -o erd_business.png`),
  T(['شکل', 'فایل منبع', 'نوع'], items.map(([a, b, c]) => [a, '`' + b + '`', c]), [3.2, 3.6, 2.2]),
  ...items.flatMap(([title, file, kind, abs]) => [
    H2(`ب-${FA(items.findIndex((i) => i[1] === file) + 1)} ${title} — \`${file}\``),
    CODE(fs.readFileSync(abs, 'utf8')),
  ]),
  H2('ب-۱۶ تنظیمات نمودارهای توالی — `sequence/mermaid-config.json`'),
  CODE(fs.readFileSync(S + 'mermaid-config.json', 'utf8')),
];
