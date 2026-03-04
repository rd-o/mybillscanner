package com.example.billscanner;

import com.google.mlkit.vision.text.Text;
import java.util.*;
import java.util.regex.*;

public class BillAnalyzer {
    private static final Map<String, long[][]> RANGES = new HashMap<>();
    static {
        RANGES.put("10", new long[][]{{67250001L, 67700000L}, {76310012L, 85139995L}});
        RANGES.put("20", new long[][]{{87280145L, 91646549L}, {118700001L, 119600000L}});
        RANGES.put("50", new long[][]{{77100001L, 77550000L}, {108050001L, 108500000L}});
    }

    public String processText(Text visionText) {
        String detectedDenom = null;
        List<String> serialsFound = new ArrayList<>();
        boolean seriesBFound = false;
        StringBuilder debugLog = new StringBuilder("DEBUG: ");

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String rawText = line.getText().toUpperCase().trim();
                debugLog.append("[").append(rawText).append("] ");

                // 1. Detect Denomination
                if (rawText.contains("DIEZ") || rawText.contains("10")) detectedDenom = "10";
                else if (rawText.contains("VEINTE") || rawText.contains("20")) detectedDenom = "20";
                else if (rawText.contains("50") || rawText.contains("CINCUENTA")) detectedDenom = "50";

                // 2. Robust Series B Detection 
                // Checks for standalone "B", "SERIE B", or strings ending/starting with B
                if (rawText.matches(".*\\bB\\b.*") || rawText.contains("SERIE B")) {
                    seriesBFound = true;
                }

                // 3. Serial Number Detection (8 to 9 digits)
                // We also replace common OCR errors: 'O' -> '0', 'I'/'L' -> '1'
                String cleanDigits = rawText.replace("O", "0").replace("I", "1").replace("L", "1");
                Matcher m = Pattern.compile("\\d{8,9}").matcher(cleanDigits);
                while (m.find()) {
                    serialsFound.add(m.group());
                }
            }
        }

        // Build the display message
        StringBuilder result = new StringBuilder();
        
        if (detectedDenom != null) {
            result.append("VALOR: ").append(detectedDenom).append(" Bs\n");
        } else {
            result.append("Buscando Denominación...\n");
        }

        result.append("SERIE B: ").append(seriesBFound ? "SI" : "NO").append("\n");
        result.append("SERIALES: ").append(serialsFound.toString()).append("\n");

        // Logic check
        if (detectedDenom != null && seriesBFound && !serialsFound.isEmpty()) {
            long[][] denomRanges = RANGES.get(detectedDenom);
            for (String s : serialsFound) {
                try {
                    long val = Long.parseLong(s);
                    for (long[] r : denomRanges) {
                        if (val >= r[0] && val <= r[1]) {
                            return "⚠️ ALERTA: " + s + " OBSERVADO\n" + result.toString();
                        }
                    }
                } catch (Exception e) { /* ignore parse errors */ }
            }
            return "✅ VALIDO\n" + result.toString();
        }

        // If something is missing, show the debug raw data at the bottom
        return result.toString() + "\n" + (debugLog.length() > 60 ? debugLog.substring(0, 60) + "..." : debugLog.toString());
    }
}
