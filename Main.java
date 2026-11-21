import java.util.ArrayList;
import java.util.List;

/**
 * Modelo NBA (misma fórmula que index.html):
 *  - Eficiencia ajustada a la media de la liga: OFF_A + DEF_B - media
 *  - Ritmo esperado: Pace_A * Pace_B / Pace_liga
 *  - Localía repartida (+1.2 local / -1.2 visita): no infla el total
 *  - Mezcla con la línea del casino y probabilidad con distribución normal
 */
class PartidoAvanzado {
    // Parámetros del modelo
    static double MEDIA_LIGA = 114.5;
    static double PACE_LIGA = 101.4;   // promedio de data/equipos.json (ESPN)
    static double PESO_MODELO = 0.35;   // 35% modelo, 65% línea
    static double SIGMA_TOTAL = 18.0;
    static double SIGMA_HC = 12.0;
    static final double LOCALIA = 1.2;   // 2.4 de margen repartido
    static final double PROB_MIN = 0.535, PROB_FUERTE = 0.555, KELLY_FRAC = 0.25;

    private final String equipoA, equipoB;
    private final double paceA, offA, defA, paceB, offB, defB;
    private final double lineaTotal, handicapLocal, cuotaTotal, cuotaHc;

    public PartidoAvanzado(String equipoA, double paceA, double offA, double defA,
                           String equipoB, double paceB, double offB, double defB,
                           double lineaTotal, double handicapLocal, double cuotaTotal, double cuotaHc) {
        this.equipoA = equipoA; this.paceA = paceA; this.offA = offA; this.defA = defA;
        this.equipoB = equipoB; this.paceB = paceB; this.offB = offB; this.defB = defB;
        this.lineaTotal = lineaTotal; this.handicapLocal = handicapLocal;
        this.cuotaTotal = cuotaTotal; this.cuotaHc = cuotaHc;
    }

    double getRitmo() { return paceA * paceB / PACE_LIGA; }
    double getProyA() { return (offA + defB - MEDIA_LIGA) / 100.0 * getRitmo() + LOCALIA; }
    double getProyB() { return (offB + defA - MEDIA_LIGA) / 100.0 * getRitmo() - LOCALIA; }
    double getTotalModelo() { return getProyA() + getProyB(); }
    double getMargenModelo() { return getProyA() - getProyB(); }
    double getTotalMezcla() { return PESO_MODELO * getTotalModelo() + (1 - PESO_MODELO) * lineaTotal; }
    double getMargenMezcla() { return PESO_MODELO * getMargenModelo() + (1 - PESO_MODELO) * (-handicapLocal); }

    double probOver() { return 1 - normCdf((lineaTotal - getTotalMezcla()) / SIGMA_TOTAL); }
    double probLocalCubre() { return 1 - normCdf((-handicapLocal - getMargenMezcla()) / SIGMA_HC); }

    // ---- utilidades de probabilidad ----
    static double erf(double x) {
        double s = Math.signum(x); x = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return s * y;
    }
    static double normCdf(double z) { return 0.5 * (1 + erf(z / Math.sqrt(2))); }
    static double decimal(double americana) { return americana > 0 ? 1 + americana / 100 : 1 + 100 / Math.abs(americana); }

    static String evaluar(String jugada, double p, double cuotaAm) {
        double d = decimal(cuotaAm), b = d - 1;
        double ev = p * d - 1;
        double kelly = Math.max(0, (p * b - (1 - p)) / b) * KELLY_FRAC;
        String nivel = (p < PROB_MIN || ev <= 0) ? "⚠️ PASAR — " : (p >= PROB_FUERTE ? "🔥 FUERTE " : "");
        return String.format("%s%s | Prob %.1f%% (mín. %.1f%%) | EV %+.1f%% | ¼ Kelly %.1f%% bank",
                nivel, jugada, p * 100, 100 / d, ev * 100, kelly * 100);
    }

    public void imprimirAnalisis() {
        double pOver = probOver(), pLocal = probLocalCubre();
        String jugadaTotal = pOver >= 0.5 ? "ALTAS " + lineaTotal : "BAJAS " + lineaTotal;
        String jugadaHc = pLocal >= 0.5
                ? String.format("%s %+.1f", equipoA, handicapLocal)
                : String.format("%s %+.1f", equipoB, -handicapLocal);

        System.out.printf("🏀 %s (local) vs %s | Total %.1f | Hándicap local %+.1f%n", equipoA, equipoB, lineaTotal, handicapLocal);
        System.out.printf("   - Posesiones estimadas: %.1f%n", getRitmo());
        System.out.printf("   - Total: modelo %.1f → mezcla %.1f%n", getTotalModelo(), getTotalMezcla());
        System.out.printf("   - Margen local: modelo %+.1f → mezcla %+.1f%n", getMargenModelo(), getMargenMezcla());
        System.out.println("   👉 TOTALES:  " + evaluar(jugadaTotal, Math.max(pOver, 1 - pOver), cuotaTotal));
        System.out.println("   👉 HÁNDICAP: " + evaluar(jugadaHc, Math.max(pLocal, 1 - pLocal), cuotaHc));
        System.out.println("--------------------------------------------------");
    }
}

public class Main {
    public static void main(String[] args) {
        List<PartidoAvanzado> partidos = new ArrayList<>();

        // Ejemplo: Cleveland (local) vs Lakers
        partidos.add(new PartidoAvanzado(
            "CLE", 102.47, 115.8, 111.5,
            "LAL", 100.64, 119.9, 113.9,
            230.5, -3.5, -110, -110
        ));

        System.out.println("📊 ANÁLISIS DE APUESTAS (MODELO AVANZADO) 📊\n");
        for (PartidoAvanzado p : partidos) {
            p.imprimirAnalisis();
        }
    }
}
