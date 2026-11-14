import java.util.ArrayList;
import java.util.List;

class PartidoAvanzado {
    private String equipoA, equipoB;
    private double paceA, offA, defA;
    private double paceB, offB, defB;
    private double lineaCasino;

    public PartidoAvanzado(String equipoA, double paceA, double offA, double defA,
                           String equipoB, double paceB, double offB, double defB, double lineaCasino) {
        this.equipoA = equipoA; this.paceA = paceA; this.offA = offA; this.defA = defA;
        this.equipoB = equipoB; this.paceB = paceB; this.offB = offB; this.defB = defB;
        this.lineaCasino = lineaCasino;
    }

    public double getRitmoPartido() {
        return (paceA + paceB) / 2.0;
    }

    public double getProyeccionTotal() {
        double ptsA_por100 = (offA + defB) / 2.0;
        double ptsB_por100 = (offB + defA) / 2.0;
        
        double proyA = (ptsA_por100 / 100.0) * getRitmoPartido();
        double proyB = (ptsB_por100 / 100.0) * getRitmoPartido();
        
        return proyA + proyB;
    }

    public String evaluarApuesta() {
        double diferencia = getProyeccionTotal() - lineaCasino;
        if (diferencia <= -4.0) return "🔥 FUERTE BAJAS (Ventaja > 4 pts)";
        if (diferencia >= 4.0) return "🔥 FUERTE ALTAS (Ventaja > 4 pts)";
        if (diferencia > 0) return "Altas (Margen ajustado)";
        return "Bajas (Margen ajustado)";
    }

    public void imprimirAnalisis() {
        System.out.printf("🏀 %s vs %s | Línea Casino: %.1f\n", equipoA, equipoB, lineaCasino);
        System.out.printf("   - Posesiones Estimadas (Pace): %.1f\n", getRitmoPartido());
        System.out.printf("   - Proyección Total: %.2f pts\n", getProyeccionTotal());
        System.out.printf("   👉 RECOMENDACIÓN: %s\n", evaluarApuesta());
        System.out.println("--------------------------------------------------");
    }
}

public class Main {
    public static void main(String[] args) {
        List<PartidoAvanzado> partidos = new ArrayList<>();

        // Ejemplo: Cleveland vs Lakers usando los datos exactos de tu imagen
        partidos.add(new PartidoAvanzado(
            "CLE", 102.47, 115.8, 111.5, 
            "LAL", 100.64, 119.9, 113.9, 
            230.5
        ));

        System.out.println("📊 ANÁLISIS DE APUESTAS (MODELO AVANZADO) 📊\n");
        for (PartidoAvanzado p : partidos) {
            p.imprimirAnalisis();
        }
    }
}