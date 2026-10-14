package com.portfolio.chungyak.external;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 공고문 PDF 한 페이지에서 괘선으로 그린 표를 찾는다 (#42).
 *
 * 공고문은 한글(HWP)에서 내보낸 PDF 라 표 테두리가 실제 선분으로 그려져 있다(2026-10-14 실측 — 공고 2997 은
 * 페이지당 가로·세로 선분 수백 개). 선분의 교차점으로 칸(cell)을 만들고, 칸끼리 이어진 덩어리를 표 하나로 본다.
 * 합친 칸(rowspan·colspan)은 칸 안에 선이 지나가지 않으므로 저절로 큰 칸 하나가 된다 — 예) "<표3> 총자산보유기준"의
 * "362,000천원 이하" 칸이 여러 행에 걸쳐 있어도 칸 하나.
 *
 * 좌표는 화면 기준(왼쪽 위 원점, y 아래로) — {@code TextPosition#getXDirAdj/getYDirAdj} 와 같다.
 * 회전된 페이지는 다루지 않는다(호출자가 빈 목록으로 본다).
 */
final class PdfTables {

    /** 같은 선·같은 점으로 볼 거리 (pt) */
    static final double TOL = 2.0;
    /** 교차점이 이보다 많으면 표 찾기를 건너뛴다 — 도면·격자 무늬 페이지에서 시간 폭주 방지 */
    private static final int MAX_POINTS = 6000;

    private PdfTables() {}

    record Cell(double x1, double y1, double x2, double y2) {
        boolean contains(double x, double y) {
            return x >= x1 - 0.5 && x <= x2 + 0.5 && y >= y1 - 0.5 && y <= y2 + 0.5;
        }

        double area() {
            return (x2 - x1) * (y2 - y1);
        }

        boolean touches(Cell o) {
            return o.x1 <= x2 + TOL && o.x2 >= x1 - TOL && o.y1 <= y2 + TOL && o.y2 >= y1 - TOL;
        }
    }

    record Table(List<Cell> cells, double x1, double y1, double x2, double y2) {
        boolean contains(double x, double y) {
            return x >= x1 - 0.5 && x <= x2 + 0.5 && y >= y1 - 0.5 && y <= y2 + 0.5;
        }
    }

    /** 가로선: a1~a2 = x 범위, c = y / 세로선: a1~a2 = y 범위, c = x */
    record Seg(double a1, double a2, double c) {}

    static List<Table> find(PDPage page) throws IOException {
        if (page.getRotation() % 360 != 0) return List.of();
        Lines lines = new Lines(page);
        lines.processPage(page);

        PDRectangle box = page.getCropBox();
        double h = box.getHeight();
        List<Seg> hs = new ArrayList<>();
        List<Seg> vs = new ArrayList<>();
        for (double[] s : lines.segments) {
            double x1 = s[0] - box.getLowerLeftX(), x2 = s[2] - box.getLowerLeftX();
            double y1 = h - (s[1] - box.getLowerLeftY()), y2 = h - (s[3] - box.getLowerLeftY());
            if (Math.abs(y1 - y2) < 1 && Math.abs(x1 - x2) > TOL) {
                hs.add(new Seg(Math.min(x1, x2), Math.max(x1, x2), (y1 + y2) / 2));
            } else if (Math.abs(x1 - x2) < 1 && Math.abs(y1 - y2) > TOL) {
                vs.add(new Seg(Math.min(y1, y2), Math.max(y1, y2), (x1 + x2) / 2));
            }
        }
        List<Seg> mergedH = merge(hs);
        List<Seg> mergedV = new ArrayList<>(merge(vs));
        mergedV.addAll(openSides(mergedH));
        return tables(cells(mergedH, merge(mergedV)));
    }

    /**
     * 바깥 세로 테두리 없이 그린 표(한글 공고문에 흔하다 — 좌우가 열린 표)의 테두리를 채운다.
     * 가로선 셋 이상이 같은 x 에서 끝나면(또는 시작하면) 그 x 에 세로선이 있는 것으로 본다.
     * 예) 공고 2997 "<표5> 월평균소득 기준"은 오른쪽 테두리가 없어 마지막 열(8인)이 칸이 되지 못했다.
     */
    static List<Seg> openSides(List<Seg> hs) {
        List<Seg> out = new ArrayList<>();
        for (boolean end : new boolean[]{true, false}) {
            List<Seg> sorted = new ArrayList<>(hs);
            sorted.sort(Comparator.comparingDouble(s -> end ? s.a2() : s.a1()));
            int i = 0;
            while (i < sorted.size()) {
                double x = end ? sorted.get(i).a2() : sorted.get(i).a1();
                List<Seg> group = new ArrayList<>();
                int j = i;
                while (j < sorted.size() && Math.abs((end ? sorted.get(j).a2() : sorted.get(j).a1()) - x) <= TOL) {
                    group.add(sorted.get(j++));
                }
                if (group.size() >= 3) {
                    group.sort(Comparator.comparingDouble(Seg::c));
                    // 세로로 가까이 이어진 가로선끼리만 한 표로 — 페이지 위아래 다른 표를 잇지 않는다
                    int start = 0;
                    for (int k = 1; k <= group.size(); k++) {
                        boolean gap = k == group.size() || group.get(k).c() - group.get(k - 1).c() > 150;
                        if (gap) {
                            if (k - start >= 3) out.add(new Seg(group.get(start).c(), group.get(k - 1).c(), x));
                            start = k;
                        }
                    }
                }
                i = j;
            }
        }
        return out;
    }

    /** 같은 직선 위에서 겹치거나 맞닿은 선분을 하나로 */
    static List<Seg> merge(List<Seg> in) {
        List<Seg> sorted = new ArrayList<>(in);
        sorted.sort(Comparator.comparingDouble(Seg::c).thenComparingDouble(Seg::a1));
        List<Seg> out = new ArrayList<>();
        for (Seg s : sorted) {
            if (!out.isEmpty()) {
                Seg last = out.get(out.size() - 1);
                if (Math.abs(last.c() - s.c()) <= TOL && s.a1() <= last.a2() + TOL) {
                    out.set(out.size() - 1, new Seg(last.a1(), Math.max(last.a2(), s.a2()), last.c()));
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    private static boolean covered(List<Seg> segs, double c, double a1, double a2) {
        for (Seg s : segs) {
            if (Math.abs(s.c() - c) <= TOL && s.a1() <= a1 + TOL && s.a2() >= a2 - TOL) return true;
        }
        return false;
    }

    private static long key(double x, double y) {
        return (Math.round(x / TOL) << 32) ^ (Math.round(y / TOL) & 0xffffffffL);
    }

    /**
     * 교차점마다 오른쪽·아래로 가장 가까운, 네 변이 다 선으로 이어진 칸을 하나 만든다.
     * 다른 칸을 품은 칸(페이지 바깥 테두리·표 외곽)은 칸이 아니라 버린다.
     */
    static List<Cell> cells(List<Seg> hs, List<Seg> vs) {
        List<double[]> points = new ArrayList<>();
        Set<Long> pointKeys = new HashSet<>();
        for (Seg hz : hs) {
            for (Seg vt : vs) {
                if (vt.c() >= hz.a1() - TOL && vt.c() <= hz.a2() + TOL
                        && hz.c() >= vt.a1() - TOL && hz.c() <= vt.a2() + TOL) {
                    points.add(new double[]{vt.c(), hz.c()});
                    if (points.size() > MAX_POINTS) return List.of();
                }
            }
        }
        for (double[] p : points) {
            // 반올림 경계에 걸린 점도 찾도록 이웃 칸에도 넣는다
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) pointKeys.add(key(p[0] + dx * TOL, p[1] + dy * TOL));
        }
        points.sort(Comparator.<double[]>comparingDouble(p -> p[1]).thenComparingDouble(p -> p[0]));

        List<Cell> found = new ArrayList<>();
        for (double[] p : points) {
            List<double[]> right = points.stream().filter(q -> Math.abs(q[1] - p[1]) <= TOL && q[0] > p[0] + TOL)
                    .sorted(Comparator.comparingDouble(q -> q[0])).toList();
            List<double[]> below = points.stream().filter(q -> Math.abs(q[0] - p[0]) <= TOL && q[1] > p[1] + TOL)
                    .sorted(Comparator.comparingDouble(q -> q[1])).toList();
            outer:
            for (double[] r : right) {
                if (!covered(hs, p[1], p[0], r[0])) break;
                for (double[] b : below) {
                    if (!covered(vs, p[0], p[1], b[1])) break;
                    if (pointKeys.contains(key(r[0], b[1]))
                            && covered(hs, b[1], p[0], r[0]) && covered(vs, r[0], p[1], b[1])) {
                        found.add(new Cell(p[0], p[1], r[0], b[1]));
                        break outer;
                    }
                }
            }
        }
        return found.stream()
                .filter(c -> found.stream().noneMatch(o -> o != c
                        && o.x1() >= c.x1() - TOL && o.x2() <= c.x2() + TOL
                        && o.y1() >= c.y1() - TOL && o.y2() <= c.y2() + TOL
                        && o.area() < c.area() * 0.95))
                .toList();
    }

    /** 맞닿은 칸끼리 묶어 표 하나로 */
    static List<Table> tables(List<Cell> cells) {
        List<Table> out = new ArrayList<>();
        boolean[] used = new boolean[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) continue;
            List<Cell> group = new ArrayList<>();
            List<Integer> queue = new ArrayList<>(List.of(i));
            used[i] = true;
            while (!queue.isEmpty()) {
                Cell c = cells.get(queue.remove(queue.size() - 1));
                group.add(c);
                for (int j = 0; j < cells.size(); j++) {
                    if (!used[j] && c.touches(cells.get(j))) {
                        used[j] = true;
                        queue.add(j);
                    }
                }
            }
            double x1 = group.stream().mapToDouble(Cell::x1).min().orElse(0);
            double y1 = group.stream().mapToDouble(Cell::y1).min().orElse(0);
            double x2 = group.stream().mapToDouble(Cell::x2).max().orElse(0);
            double y2 = group.stream().mapToDouble(Cell::y2).max().orElse(0);
            group.sort(Comparator.comparingDouble(Cell::y1).thenComparingDouble(Cell::x1));
            out.add(new Table(group, x1, y1, x2, y2));
        }
        out.sort(Comparator.comparingDouble(Table::y1));
        return out;
    }

    /** 그려진(stroke) 선분과, 선 대신 쓰는 채운 얇은 사각형만 모은다. 클리핑 경로는 버린다. */
    private static final class Lines extends PDFGraphicsStreamEngine {
        final List<double[]> segments = new ArrayList<>();
        private final List<double[]> path = new ArrayList<>();
        private Point2D current;
        private Point2D start;

        Lines(PDPage page) {
            super(page);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            add(p0, p1);
            add(p1, p2);
            add(p2, p3);
            add(p3, p0);
        }

        private void add(Point2D a, Point2D b) {
            path.add(new double[]{a.getX(), a.getY(), b.getX(), b.getY()});
        }

        @Override
        public void moveTo(float x, float y) {
            current = start = new Point2D.Float(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            Point2D next = new Point2D.Float(x, y);
            if (current != null) add(current, next);
            current = next;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            current = new Point2D.Float(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return current;
        }

        @Override
        public void closePath() {
            if (current != null && start != null) add(current, start);
            current = start;
        }

        @Override
        public void endPath() {
            path.clear();
        }

        @Override
        public void strokePath() {
            segments.addAll(path);
            path.clear();
        }

        @Override
        public void fillPath(int windingRule) {
            thinFilledRect();
            path.clear();
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            segments.addAll(path);
            path.clear();
        }

        /** 한글 PDF 는 선을 아주 얇은 채운 사각형으로 그리기도 한다 — 두께가 얇으면 선 하나로 */
        private void thinFilledRect() {
            if (path.isEmpty()) return;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] s : path) {
                minX = Math.min(minX, Math.min(s[0], s[2]));
                maxX = Math.max(maxX, Math.max(s[0], s[2]));
                minY = Math.min(minY, Math.min(s[1], s[3]));
                maxY = Math.max(maxY, Math.max(s[1], s[3]));
            }
            if (maxY - minY < 2.5 && maxX - minX > 3) {
                segments.add(new double[]{minX, (minY + maxY) / 2, maxX, (minY + maxY) / 2});
            } else if (maxX - minX < 2.5 && maxY - minY > 3) {
                segments.add(new double[]{(minX + maxX) / 2, minY, (minX + maxX) / 2, maxY});
            }
        }

        @Override
        public void drawImage(PDImage pdImage) {}

        @Override
        public void clip(int windingRule) {}

        @Override
        public void shadingFill(COSName shadingName) {}
    }
}
