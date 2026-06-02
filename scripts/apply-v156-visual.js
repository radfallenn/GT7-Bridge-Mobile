const fs = require('fs');

const file = 'app/src/main/java/com/gt7/bridge/mobile/MainActivity.java';
let s = fs.readFileSync(file, 'utf8');

s = s.replace('private static final String VERSION = "1.5.5";', 'private static final String VERSION = "1.5.6";');
s = s.replace('        shell.addView(titleBar());\n        shell.addView(tabs());\n', '        shell.addView(titleBar());\n');
s = s.replace('        wrap.addView(grid2(card("VOLTAS CORRIGIDAS", "voltasCorrigidas", BLUE), targetCard()));\n        wrap.addView(grid2(card("ESTADO DA CORRIDA", "estadoCorrida", BLUE), card("PARADAS BOXES", "paradasBoxes", TXT)));', '        wrap.addView(grid2(card("VOLTAS CORRIGIDAS", "voltasCorrigidas", BLUE), card("ESTADO DA CORRIDA", "estadoCorrida", BLUE)));\n        wrap.addView(grid2(card("PARADAS BOXES", "paradasBoxes", TXT), card("VELOCIDADE MÁXIMA", "velocidadeMaxima", YELLOW)));');
s = s.replace('        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(96));', '        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(112));');
s = s.replace('        TextView value = text("0%", 11, color, true);', '        TextView value = text("0%", 13, color, true);');
s = s.replace('        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(18));', '        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(26));');
s = s.replace('        bar.setProgress(0);\n        LinearLayout.LayoutParams blp', '        bar.setProgress(0);\n        bar.getProgressDrawable().setTint(color);\n        LinearLayout.LayoutParams blp');

fs.writeFileSync(file, s);
console.log('Patch visual v156 aplicado');
