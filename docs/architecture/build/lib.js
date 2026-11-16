// Builder for the Asan Zeman architecture document (RTL Persian, original visual style)
const fs = require('fs');
const path = require('path');
const d = require('docx');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  AlignmentType, WidthType, ShadingType, BorderStyle, HeadingLevel, LevelFormat,
  Header, Footer, PageNumber, TableOfContents, PageBreak, HeightRule, VerticalAlign,
  TableLayoutType,
} = d;

const C = { navy: '1A2330', accent: 'D4875A', band: 'F8F0EB', grey: '6B7280', light: 'B0B8C0', text: '1F2937', code: 'F3F4F6' };
const FONT = { ascii: 'Calibri', hAnsi: 'Calibri', cs: 'Tahoma', eastAsia: 'Tahoma' };
const MONO = { ascii: 'Consolas', hAnsi: 'Consolas', cs: 'Consolas', eastAsia: 'Consolas' };
const PAGE_W = 11906, MARGIN = 1300, CONTENT_W = PAGE_W - 2 * MARGIN; // 9306 DXA

// Split plain text into Persian (RTL) and Latin (LTR) runs so Word's bidi algorithm orders them correctly.
const LTR_RE = /[A-Za-z][A-Za-z0-9_.\-\/:@#=+,'"<>%*&|~ ]*[A-Za-z0-9_%]|[A-Za-z]/g;
function scriptRuns(text, base) {
  const out = []; let last = 0, m;
  LTR_RE.lastIndex = 0;
  while ((m = LTR_RE.exec(text)) !== null) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), rightToLeft: true, font: FONT, ...base }));
    out.push(new TextRun({ text: m[0], font: FONT, ...base }));
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), rightToLeft: true, font: FONT, ...base }));
  return out;
}
// Directional marks only where a code token starts or ends with a neutral character (e.g. '/api/...').
const ltrWrap = (t) => (/^[A-Za-z0-9]/.test(t) ? '' : '\u200E') + t + (/[A-Za-z0-9_)\]]$/.test(t) ? '' : '\u200E');
// Inline markup: **bold**, `code` (code is always one LTR monospace run)
function runs(text, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0, m;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(...scriptRuns(text.slice(last, m.index), base));
    const tok = m[0];
    if (tok.startsWith('**')) out.push(...scriptRuns(tok.slice(2, -2), { ...base, bold: true }));
    else out.push(new TextRun({ text: ltrWrap(tok.slice(1, -1)), font: MONO, color: base.color || '7A3E1D', ...base, size: (base.size || 20) - 2 }));
    last = m.index + tok.length;
  }
  if (last < text.length) out.push(...scriptRuns(text.slice(last), base));
  return out;
}

const P = (t, o = {}) => new Paragraph({ bidirectional: true, alignment: AlignmentType.JUSTIFIED, spacing: { after: 120, line: 300 }, children: runs(t, o.run), ...o.para });
const H1 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_1, bidirectional: true, pageBreakBefore: true, children: runs(t) });
const H2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2, bidirectional: true, children: runs(t) });
const H3 = (t, o = {}) => new Paragraph({ heading: HeadingLevel.HEADING_3, bidirectional: true, children: runs(t), ...o });
const BUL = (items, level = 0) => items.flatMap((it) => Array.isArray(it)
  ? BUL(it, level + 1)
  : [new Paragraph({ bidirectional: true, alignment: AlignmentType.JUSTIFIED, numbering: { reference: 'bul', level }, spacing: { after: 60, line: 290 }, children: runs(it) })]);
const NUM = (items, ref) => items.map((it) => new Paragraph({ bidirectional: true, alignment: AlignmentType.JUSTIFIED, numbering: { reference: ref, level: 0 }, spacing: { after: 60, line: 290 }, children: runs(it) }));
// LTR code / config block
const CODE = (txt) => {
  const lines = txt.replace(/\n$/, '').split('\n');
  return [new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: [CONTENT_W],
    rows: [new TableRow({ children: [new TableCell({
      width: { size: CONTENT_W, type: WidthType.DXA },
      shading: { type: ShadingType.CLEAR, color: 'auto', fill: C.code },
      margins: { top: 100, bottom: 100, left: 160, right: 160 },
      borders: { top: { style: BorderStyle.SINGLE, size: 4, color: 'D1D5DB' }, bottom: { style: BorderStyle.SINGLE, size: 4, color: 'D1D5DB' }, left: { style: BorderStyle.SINGLE, size: 4, color: 'D1D5DB' }, right: { style: BorderStyle.SINGLE, size: 4, color: 'D1D5DB' } },
      children: lines.map((l) => new Paragraph({ bidirectional: false, alignment: AlignmentType.LEFT, spacing: { after: 0, line: 260 }, children: [new TextRun({ text: l || ' ', font: MONO, size: 16, color: '111827' })] })),
    })] })],
  }), new Paragraph({ spacing: { after: 80 }, children: [] })];
};
const cellBorder = { style: BorderStyle.SINGLE, size: 4, color: 'E5D5CA' };
// Table: headers[], rows[][], widths[] (relative), visually right-to-left
function T(headers, rows, widths) {
  const sum = widths.reduce((a, b) => a + b, 0);
  const cw = widths.map((w) => Math.floor((w / sum) * CONTENT_W));
  cw[cw.length - 1] += CONTENT_W - cw.reduce((a, b) => a + b, 0);
  const mk = (txt, i, hdr, band) => new TableCell({
    width: { size: cw[i], type: WidthType.DXA },
    shading: { type: ShadingType.CLEAR, color: 'auto', fill: hdr ? C.accent : (band ? C.band : 'FFFFFF') },
    margins: { top: 60, bottom: 60, left: 90, right: 90 },
    borders: { top: cellBorder, bottom: cellBorder, left: cellBorder, right: cellBorder },
    verticalAlign: VerticalAlign.CENTER,
    children: String(txt).split('\n').map((line) => new Paragraph({ bidirectional: true, alignment: hdr ? AlignmentType.CENTER : AlignmentType.START, spacing: { after: 20, line: 260 }, children: runs(line, hdr ? { bold: true, color: 'FFFFFF', size: 18 } : { size: 18 }) })),
  });
  return [new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: cw, visuallyRightToLeft: true, layout: TableLayoutType.FIXED,
    rows: [new TableRow({ tableHeader: true, children: headers.map((h, i) => mk(h, i, true)) }),
      ...rows.map((r, ri) => new TableRow({ cantSplit: true, children: r.map((c, i) => mk(c, i, false, ri % 2 === 0)) }))],
  }), new Paragraph({ spacing: { after: 100 }, children: [] })];
}
function IMG(file, caption, maxW = 6.3, maxH = 8.6) {
  const sizeOf = require('child_process').execSync(`python3 -c "from PIL import Image; im=Image.open('${file}'); print(im.width, im.height)"`).toString().trim().split(' ').map(Number);
  const [w, h] = sizeOf; const wIn = maxW; let hIn = (h / w) * wIn;
  let fw = wIn; if (hIn > maxH) { fw = wIn * (maxH / hIn); hIn = maxH; }
  return [new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120, after: 60 }, children: [new ImageRun({ type: 'png', data: fs.readFileSync(file), transformation: { width: Math.round(fw * 96), height: Math.round(hIn * 96) }, altText: { title: caption, description: caption, name: path.basename(file) } })] }),
    new Paragraph({ bidirectional: true, alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: caption, italics: true, rightToLeft: true, font: FONT, size: 18, color: C.grey })] })];
}

function cover(meta) {
  const line = (t, o) => new Paragraph({ bidirectional: true, alignment: AlignmentType.RIGHT, spacing: { after: o.after ?? 120 }, children: [new TextRun({ text: t, rightToLeft: true, font: o.font || FONT, ...o })] });
  const rule = () => new Paragraph({ spacing: { before: 200, after: 200 }, border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: C.accent, space: 1 } }, children: [] });
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: [CONTENT_W],
    rows: [new TableRow({ height: { value: 13800, rule: HeightRule.EXACT }, children: [new TableCell({
      width: { size: CONTENT_W, type: WidthType.DXA }, shading: { type: ShadingType.CLEAR, color: 'auto', fill: C.navy },
      margins: { top: 900, bottom: 400, left: 700, right: 700 },
      borders: { top: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, bottom: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, left: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' }, right: { style: BorderStyle.NONE, size: 0, color: 'FFFFFF' } },
      children: [
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 1400, after: 200 }, children: [new TextRun({ text: 'A R C H I T E C T U R E   D O C U M E N T', font: FONT, size: 16, color: C.accent, characterSpacing: 40 })] }),
        rule(),
        line('سند معماری سامانه', { size: 64, bold: true, color: 'FFFFFF', after: 60 }),
        line('آسان ضمان', { size: 64, bold: true, color: 'FFFFFF', after: 200 }),
        line('سامانه صدور ضمانتنامه بازگشت از سفر', { size: 26, color: C.light, after: 600 }),
        line(meta.version, { size: 20, color: C.light }),
        line(meta.date, { size: 20, color: C.light }),
        line('مخاطب: تیم فنی (معماران و توسعه‌دهندگان)', { size: 20, color: C.light }),
        line(meta.basis, { size: 18, color: C.light, after: 1600 }),
        rule(),
        new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'Confidential — Internal Use Only', font: FONT, size: 14, color: '687078' })] }),
      ],
    })] })],
  });
}

function build(blocks, meta, outFile) {
  const headingStyle = (id, name, size, color, before, after, outline) => ({
    id, name, basedOn: 'Normal', next: 'Normal', quickFormat: true,
    run: { font: FONT, size, sizeComplexScript: size, bold: true, boldComplexScript: true, color, rightToLeft: true },
    paragraph: { spacing: { before, after }, keepNext: true, keepLines: true, outlineLevel: outline, bidirectional: true },
  });
  const levels = [0, 1].map((lvl) => ({ level: lvl, format: LevelFormat.BULLET, text: lvl === 0 ? '●' : '○', alignment: AlignmentType.LEFT,
    style: { paragraph: { indent: { left: 540 + lvl * 360, hanging: 300 } }, run: { font: FONT, size: 14, color: C.accent } } }));
  const numCfg = (ref) => ({ reference: ref, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } }, run: { font: FONT } } }] });
  const hdr = () => ({ default: new Header({ children: [new Paragraph({ bidirectional: true, alignment: AlignmentType.CENTER, border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E5E7EB', space: 4 } }, children: [new TextRun({ text: 'سند معماری سامانه آسان ضمان — ' + meta.versionShort, rightToLeft: true, font: FONT, size: 16, color: '90989F' })] })] }) });
  const ftr = () => ({ default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18, color: C.grey })] })] }) });
  const portrait = { size: { width: PAGE_W, height: 16838 }, margin: { top: 1300, bottom: 1200, left: MARGIN, right: MARGIN, header: 600, footer: 600 } };
  const landscape = { size: { width: PAGE_W, height: 16838, orientation: d.PageOrientation.LANDSCAPE }, margin: { top: 1300, bottom: 1200, left: MARGIN, right: MARGIN, header: 600, footer: 600 } };
  // blocks may contain { landscape: true, children: [...] } markers; each becomes its own landscape section
  const sections = []; let cur = [];
  const flush = () => { if (cur.length) sections.push({ properties: { page: portrait, type: d.SectionType.NEXT_PAGE }, headers: hdr(), footers: ftr(), children: cur }); cur = []; };
  for (const b of blocks.flat(Infinity)) {
    if (b && b.landscape) { flush(); sections.push({ properties: { page: landscape, type: d.SectionType.NEXT_PAGE }, headers: hdr(), footers: ftr(), children: b.children.flat(Infinity) }); }
    else cur.push(b);
  }
  flush();
  const doc = new Document({
    creator: 'Asan Zeman technical team', title: 'سند معماری سامانه آسان ضمان', description: meta.version,
    features: { updateFields: true },
    styles: {
      default: { document: { run: { font: FONT, size: 20, sizeComplexScript: 20, color: C.text, rightToLeft: true }, paragraph: { bidirectional: true } } },
      paragraphStyles: [
        headingStyle('Heading1', 'Heading 1', 34, C.navy, 240, 200, 0),
        headingStyle('Heading2', 'Heading 2', 26, C.navy, 280, 120, 1),
        headingStyle('Heading3', 'Heading 3', 22, '7A4A30', 200, 100, 2),
      ],
    },
    numbering: { config: [{ reference: 'bul', levels }, ...meta.numberLists.map(numCfg)] },
    sections: [
      { properties: { page: { size: { width: PAGE_W, height: 16838 }, margin: { top: 1200, bottom: 1000, left: MARGIN, right: MARGIN } } },
        children: [cover(meta)] },
      ...sections,
    ],
  });
  return Packer.toBuffer(doc).then((buf) => fs.writeFileSync(outFile, buf));
}

const TOC = () => [new Paragraph({ bidirectional: true, alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: 'فهرست مطالب', bold: true, rightToLeft: true, font: FONT, size: 28, color: C.navy })] }),
  new TableOfContents('فهرست مطالب', { hyperlink: true, headingStyleRange: '1-2' })];

const LANDSCAPE = (children) => ({ landscape: true, children });
module.exports = { LANDSCAPE, runs, P, H1, H2, H3, BUL, NUM, CODE, T, IMG, TOC, build, PageBreak, Paragraph };
