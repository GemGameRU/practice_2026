package com.floydwarshall.view;

import com.floydwarshall.model.Graph;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class GraphCanvas extends Canvas {

    public interface SelectionListener {
        void onSelection(SelectionType type, int a, int b);
    }

    public enum SelectionType {
        VERTEX, EDGE, NONE
    }

    // Визуальные константы
    private static final double VERTEX_RADIUS = 18.0;
    private static final double ARROW_SIZE = 10.0;
    private static final double EDGE_CURVATURE = 18.0;
    private static final double PADDING = 40.0;

    // Цвета
    private static final Color BG_COLOR = Color.web("#fafafa");
    private static final Color VERTEX_FILL = Color.web("#ffffff");
    private static final Color VERTEX_STROKE = Color.web("#37474f");
    private static final Color EDGE_COLOR = Color.web("#607d8b");
    private static final Color WEIGHT_FILL = Color.web("#37474f");
    private static final Color HIGHLIGHT_BLUE = Color.web("#4fc3f7");
    private static final Color HIGHLIGHT_YELLOW = Color.web("#fff176");
    private static final Color K_VERTEX_FILL = Color.web("#29b6f6");

    // Состояние
    private Graph graph;
    private String title;
    private boolean editableSource; // true — холст 1, false — холст 2

    // Выделение пользователем
    private SelectionType selType = SelectionType.NONE;
    private int selA = -1;
    private int selB = -1;

    // Подсветка алгоритма (для холста 2)
    private int highlightI = -1, highlightJ = -1, highlightK = -1;

    // Прокрутка
    private double scrollX = 0;
    private double scrollY = 0;

    private SelectionListener listener;

    public GraphCanvas(double width, double height, String title, boolean editableSource) {
        super(width, height);
        this.title = title;
        this.editableSource = editableSource;

        setupInteractions();
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    /** Устанавливает отображаемый граф и перерисовывает холст. */
    public void setGraph(Graph graph) {
        this.graph = graph;
        scrollX = 0;
        scrollY = 0;
        clearSelection();
        draw();
    }

    /** Подсветка элементов текущей итерации алгоритма (k, i, j). */
    public void setAlgorithmHighlight(int i, int j, int k) {
        this.highlightI = i;
        this.highlightJ = j;
        this.highlightK = k;
        draw();
    }

    public void clearAlgorithmHighlight() {
        this.highlightI = -1;
        this.highlightJ = -1;
        this.highlightK = -1;
        draw();
    }

    /** Выделяет вершину (для синхронизации из таблицы). */
    public void selectVertex(int v) {
        selType = SelectionType.VERTEX;
        selA = v;
        selB = -1;
        draw();
    }

    /** Выделяет ребро (для синхронизации из таблицы). */
    public void selectEdge(int from, int to) {
        selType = SelectionType.EDGE;
        selA = from;
        selB = to;
        draw();
    }

    public void clearSelection() {
        selType = SelectionType.NONE;
        selA = -1;
        selB = -1;
        draw();
    }

    private void setupInteractions() {
        // Прокрутка колесом мыши.
        setOnScroll((ScrollEvent e) -> {
            if (e.isShiftDown()) {
                scrollX += e.getDeltaY();
            } else {
                scrollY += e.getDeltaY();
            }
            draw();
            e.consume();
        });

        // Выделение элементов кликом.
        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            double mx = e.getX();
            double my = e.getY();

            // Сначала проверяем попадание в вершину.
            int hitVertex = hitTestVertex(mx, my);
            if (hitVertex >= 0) {
                selType = SelectionType.VERTEX;
                selA = hitVertex;
                selB = -1;
                draw();
                if (listener != null) {
                    listener.onSelection(SelectionType.VERTEX, hitVertex, -1);
                }
                return;
            }

            // Затем — в ребро.
            int[] hitEdge = hitTestEdge(mx, my);
            if (hitEdge != null) {
                selType = SelectionType.EDGE;
                selA = hitEdge[0];
                selB = hitEdge[1];
                draw();
                if (listener != null) {
                    listener.onSelection(SelectionType.EDGE, hitEdge[0], hitEdge[1]);
                }
                return;
            }

            // Клик по пустому месту — снятие выделения.
            clearSelection();
            if (listener != null) {
                listener.onSelection(SelectionType.NONE, -1, -1);
            }
        });
    }

    private double[] vertexPos(int i, int n) {
        double cx = getWidth() / 2.0 + scrollX;
        double cy = getHeight() / 2.0 + scrollY;
        double radius = Math.min(getWidth(), getHeight()) / 2.0 - PADDING - VERTEX_RADIUS;
        radius = Math.max(radius, 40.0);
        double angle = 2.0 * Math.PI * i / n - Math.PI / 2.0;
        return new double[] {
                cx + radius * Math.cos(angle),
                cy + radius * Math.sin(angle)
        };
    }

    private int hitTestVertex(double mx, double my) {
        if (graph == null)
            return -1;
        int n = graph.size();
        for (int i = 0; i < n; i++) {
            double[] p = vertexPos(i, n);
            if (Math.hypot(mx - p[0], my - p[1]) <= VERTEX_RADIUS + 2) {
                return i;
            }
        }
        return -1;
    }

    private int[] hitTestEdge(double mx, double my) {
        if (graph == null)
            return null;
        int n = graph.size();
        double bestDist = 8.0;
        int[] best = null;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j || !graph.hasEdge(i, j))
                    continue;
                double[] pa = vertexPos(i, n);
                double[] pb = vertexPos(j, n);
                double[] mid = { (pa[0] + pb[0]) / 2.0, (pa[1] + pb[1]) / 2.0 };
                // Перпендикуляр для изгиба
                double dx = pb[0] - pa[0];
                double dy = pb[1] - pa[1];
                double len = Math.hypot(dx, dy);
                if (len < 1e-6)
                    continue;
                double perpX = -dy / len;
                double perpY = dx / len;
                double ctrlX = mid[0] + perpX * EDGE_CURVATURE;
                double ctrlY = mid[1] + perpY * EDGE_CURVATURE;
                // Точка на кривой при t=0.5
                double curveX = 0.25 * pa[0] + 0.5 * ctrlX + 0.25 * pb[0];
                double curveY = 0.25 * pa[1] + 0.5 * ctrlY + 0.25 * pb[1];
                double d = Math.hypot(mx - curveX, my - curveY);
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] { i, j };
                }
            }
        }
        return best;
    }

    /** Полная перерисовка холста. */
    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, getWidth(), getHeight());

        // Заголовок.
        gc.setFill(Color.web("#37474f"));
        gc.setFont(Font.font("SansSerif", 13));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(title, 8, 18);

        if (graph == null) {
            return;
        }

        int n = graph.size();
        if (n == 0) {
            return;
        }

        // 1. Рёбра.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j || !graph.hasEdge(i, j))
                    continue;
                drawEdge(gc, i, j, n);
            }
        }

        // 2. Вершины.
        for (int i = 0; i < n; i++) {
            drawVertex(gc, i, n);
        }
    }

    private void drawVertex(GraphicsContext gc, int i, int n) {
        double[] p = vertexPos(i, n);

        boolean isAlgI = (i == highlightI);
        boolean isAlgJ = (i == highlightJ);
        boolean isAlgK = (i == highlightK);
        boolean isAlg = isAlgI || isAlgJ || isAlgK;
        boolean isSel = (selType == SelectionType.VERTEX && selA == i);

        double r = isAlgK ? VERTEX_RADIUS + 4 : VERTEX_RADIUS;
        if (isSel) {
            r += 2;
        }

        // Подсветка.
        if (isAlgK) {
            gc.setFill(K_VERTEX_FILL);
        } else if (isAlg) {
            gc.setFill(HIGHLIGHT_BLUE);
        } else {
            gc.setFill(VERTEX_FILL);
        }
        gc.setStroke(isSel ? HIGHLIGHT_YELLOW : (isAlg ? HIGHLIGHT_BLUE : VERTEX_STROKE));
        gc.setLineWidth(isSel ? 3.0 : 2.0);
        gc.fillOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
        gc.strokeOval(p[0] - r, p[1] - r, 2 * r, 2 * r);

        // Номер.
        gc.setFill(Color.web("#212121"));
        gc.setFont(Font.font("SansSerif", 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.valueOf(i), p[0], p[1] + 5);
    }

    private void drawEdge(GraphicsContext gc, int i, int j, int n) {
        double[] pa = vertexPos(i, n);
        double[] pb = vertexPos(j, n);

        double dx = pb[0] - pa[0];
        double dy = pb[1] - pa[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-6)
            return;

        double ux = dx / len;
        double uy = dy / len;
        double perpX = -uy;
        double perpY = ux;

        // Точки начала и конца с учётом радиуса вершин.
        double startX = pa[0] + ux * VERTEX_RADIUS;
        double startY = pa[1] + uy * VERTEX_RADIUS;
        double endX = pb[0] - ux * (VERTEX_RADIUS + 2);
        double endY = pb[1] - uy * (VERTEX_RADIUS + 2);

        // Контрольная точка для изгиба (разделяет встречные рёбра).
        double midX = (startX + endX) / 2.0;
        double midY = (startY + endY) / 2.0;
        double ctrlX = midX + perpX * EDGE_CURVATURE;
        double ctrlY = midY + perpY * EDGE_CURVATURE;

        // Подсветка.
        boolean isAlgEdge = (i == highlightI && j == highlightJ) ||
                (i == highlightI && j == highlightK) ||
                (i == highlightK && j == highlightJ);
        boolean isSel = (selType == SelectionType.EDGE && selA == i && selB == j);

        if (isAlgEdge) {
            gc.setStroke(HIGHLIGHT_BLUE);
            gc.setLineWidth(3.0);
        } else if (isSel) {
            gc.setStroke(HIGHLIGHT_YELLOW);
            gc.setLineWidth(3.0);
        } else {
            gc.setStroke(EDGE_COLOR);
            gc.setLineWidth(1.5);
        }

        // Рисуем квадр. кривую Безье.
        gc.beginPath();
        gc.moveTo(startX, startY);
        gc.quadraticCurveTo(ctrlX, ctrlY, endX, endY);
        gc.stroke();

        // Стрелка в конце. Касательная в конце = (end - ctrl).
        double tdx = endX - ctrlX;
        double tdy = endY - ctrlY;
        double tlen = Math.hypot(tdx, tdy);
        if (tlen > 1e-6) {
            tdx /= tlen;
            tdy /= tlen;
            double arrowLeft = -tdy;
            double arrowUp = tdx;
            gc.setFill(isAlgEdge ? HIGHLIGHT_BLUE : (isSel ? HIGHLIGHT_YELLOW : EDGE_COLOR));
            gc.beginPath();
            gc.moveTo(endX, endY);
            gc.lineTo(endX - tdx * ARROW_SIZE + arrowLeft * ARROW_SIZE * 0.5,
                    endY - tdy * ARROW_SIZE + arrowUp * ARROW_SIZE * 0.5);
            gc.lineTo(endX - tdx * ARROW_SIZE - arrowLeft * ARROW_SIZE * 0.5,
                    endY - tdy * ARROW_SIZE - arrowUp * ARROW_SIZE * 0.5);
            gc.closePath();
            gc.fill();
        }

        // Подпись веса.
        double labelX = 0.25 * startX + 0.5 * ctrlX + 0.25 * endX;
        double labelY = 0.25 * startY + 0.5 * ctrlY + 0.25 * endY;
        Integer w = graph.get(i, j);
        String label = (w == null) ? "inf" : String.valueOf(w);

        // Подложка под текстом для читаемости.
        gc.setFont(Font.font("SansSerif", 11));
        double textW = label.length() * 6.5 + 6;
        gc.setFill(Color.web("#fafafa"));
        gc.fillRect(labelX - textW / 2.0, labelY - 9, textW, 14);

        gc.setFill(WEIGHT_FILL);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, labelX, labelY + 3);
    }

    public boolean isEditableSource() {
        return editableSource;
    }

    public int getSelectedVertex() {
        return (selType == SelectionType.VERTEX) ? selA : -1;
    }

    public int[] getSelectedEdge() {
        return (selType == SelectionType.EDGE) ? new int[] { selA, selB } : null;
    }
}
