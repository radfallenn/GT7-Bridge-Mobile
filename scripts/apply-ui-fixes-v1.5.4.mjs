import fs from 'node:fs';

const file = 'app/src/main/java/com/gt7/bridge/mobile/MainActivity.java';
let src = fs.readFileSync(file, 'utf8');

const marker = 'GT7_UI_FIXES_V1_5_4';
if (src.includes(marker)) {
  console.log('v1.5.4 já aplicado.');
  process.exit(0);
}

src = src.replace('private static final String VERSION = "1.5.3";', 'private static final String VERSION = "1.5.4"; // GT7_UI_FIXES_V1_5_4');

src = src.replace(
  '        wrap.addView(grid2(metricCard("COMBUSTÍVEL ⏻", "combustivel", "Liters", TXT, "--"), metricCard("COMBUSTÍVEL\\nPORCENTAGEM", "combustivelPct", "%", CYAN, "--")));\n        wrap.addView(grid2(metricCard("TEMPERATURA DA ÁGUA", "tempAgua", "", CYAN, "--"), metricCard("TEMPERATURA DO ÓLEO", "tempOleo", "", ORANGE, "--")));',
  '        wrap.addView(grid2(metricCard("COMBUSTÍVEL ⏻", "combustivel", "Liters", TXT, "--"), metricCard("COMBUSTÍVEL\\nPORCENTAGEM", "combustivelPct", "%", CYAN, "--")));'
);

src = src.replace(
  '        setValue("tempAgua", t.tempAgua); setValue("tempOleo", t.tempOleo);',
  ''
);

src = src.replace(
  '        String combustivel = "--", combustivelPct = "--", tempAgua = "--", tempOleo = "--", melhorVolta = "--", ultimaVolta = "--", tempoTotal = "--", voltasBrutas = "0", voltasCorrigidas = "0", estadoCorrida = "◔ EM ANDAMENTO", paradasBoxes = "0", turbo = "--", vetorVelocidade = "--", rollPitch = "Pitch: --°\\nRoll: --°", yaw = "--", warning = "Sem fluxo de dados";',
  '        String combustivel = "--", combustivelPct = "--", melhorVolta = "--", ultimaVolta = "--", tempoTotal = "--", voltasBrutas = "0", voltasCorrigidas = "0", estadoCorrida = "◔ EM ANDAMENTO", paradasBoxes = "0", turbo = "--", vetorVelocidade = "--", rollPitch = "Pitch: --°\\nRoll: --°", yaw = "--", warning = "Sem fluxo de dados";'
);

src = src.replace(
  'melhorVolta = j.optString("melhorVolta",melhorVolta); ultimaVolta = j.optString("ultimaVolta",ultimaVolta); tempoTotal = j.optString("tempoTotalCorrida",tempoTotal); voltasBrutas = String.valueOf(j.optInt("voltasCompletadas", parseIntSafe(voltasBrutas))); voltasCorrigidas = String.valueOf(j.optInt("voltasCorrigidas", parseIntSafe(voltasCorrigidas))); warning = j.optString("warning", connected ? "Dados fluindo normalmente" : "Sem fluxo de dados");',
  'melhorVolta = j.optString("melhorVolta",melhorVolta); ultimaVolta = j.optString("ultimaVolta",ultimaVolta); voltasBrutas = String.valueOf(j.optInt("voltasCompletadas", parseIntSafe(voltasBrutas))); voltasCorrigidas = String.valueOf(j.optInt("voltasCorrigidas", parseIntSafe(voltasCorrigidas))); tempoTotal = calcularTempoTotalCorrigido(melhorVolta, ultimaVolta, voltasCorrigidas); warning = j.optString("warning", connected ? "Dados fluindo normalmente" : "Sem fluxo de dados");'
);

src = src.replace(
  '        private static int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }\n',
  `        private static int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }\n\n        private static String calcularTempoTotalCorrigido(String melhor, String ultima, String voltasCorrigidas) {\n            int laps = parseIntSafe(voltasCorrigidas);\n            if (laps <= 0) return "--";\n            long baseMs = parseTempoMs(ultima);\n            if (baseMs <= 0) baseMs = parseTempoMs(melhor);\n            if (baseMs <= 0) return "--";\n            return formatTempoMs(baseMs * laps);\n        }\n\n        private static long parseTempoMs(String s) {\n            try {\n                if (s == null || s.equals("--") || s.trim().isEmpty()) return 0;\n                String[] minSplit = s.trim().split(":");\n                long min = 0;\n                String secPart = s.trim();\n                if (minSplit.length == 2) {\n                    min = Long.parseLong(minSplit[0]);\n                    secPart = minSplit[1];\n                }\n                String[] secSplit = secPart.split("\\\\.");\n                long sec = Long.parseLong(secSplit[0]);\n                long ms = 0;\n                if (secSplit.length > 1) {\n                    String raw = (secSplit[1] + "000").substring(0, 3);\n                    ms = Long.parseLong(raw);\n                }\n                return min * 60000L + sec * 1000L + ms;\n            } catch (Exception e) {\n                return 0;\n            }\n        }\n\n        private static String formatTempoMs(long totalMs) {\n            long minutes = totalMs / 60000L;\n            long seconds = (totalMs % 60000L) / 1000L;\n            long ms = totalMs % 1000L;\n            return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, ms);\n        }\n`
);

fs.writeFileSync(file, src);
console.log('UI fixes v1.5.4 aplicados.');
