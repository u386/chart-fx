package de.gsi.chart.renderer.spi.financial;

import static org.junit.jupiter.api.Assertions.*;

import static de.gsi.chart.renderer.spi.financial.css.FinancialColorSchemeConstants.*;

import java.security.InvalidParameterException;
import java.util.Calendar;

import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opentest4j.AssertionFailedError;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.AxisLabelOverlapPolicy;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.renderer.spi.financial.css.FinancialColorSchemeConfig;
import de.gsi.chart.renderer.spi.financial.service.footprint.FootprintRendererAttributes;
import de.gsi.chart.renderer.spi.financial.utils.CalendarUtils;
import de.gsi.chart.renderer.spi.financial.utils.FinancialTestUtils;
import de.gsi.chart.renderer.spi.financial.utils.FinancialTestUtils.TestChart;
import de.gsi.chart.renderer.spi.financial.utils.FootprintRenderedAPIDummyAdapter;
import de.gsi.chart.renderer.spi.financial.utils.Interval;
import de.gsi.chart.ui.utils.JavaFXInterceptorUtils.SelectiveJavaFxInterceptor;
import de.gsi.chart.ui.utils.TestFx;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.spi.AbstractDataSet;
import de.gsi.dataset.spi.financial.OhlcvDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;

@ExtendWith(ApplicationExtension.class)
@ExtendWith(SelectiveJavaFxInterceptor.class)
public class FootprintRendererTest {
    private FootprintRenderer rendererTested;
    private XYChart chart;
    private OhlcvDataSet ohlcvDataSet;
    private final String[] schemes = getDefaultColorSchemes();

    @Start
    public void start(Stage stage) throws Exception {
        for (String scheme : schemes) {
            financialComponentTest(stage, scheme);
        }
    }

    private void financialComponentTest(Stage stage, String scheme) throws Exception {
        ProcessingProfiler.setDebugState(false); // enable for detailed renderer tracing
        ohlcvDataSet = new OhlcvDataSet("ohlc1");
        ohlcvDataSet.setData(FinancialTestUtils.createTestOhlcv());
        FootprintRendererAttributes footprintAttrs = FootprintRendererAttributes.getDefaultValues(scheme);
        rendererTested = new FootprintRenderer(
                new FootprintRenderedAPIDummyAdapter(footprintAttrs),
                true,
                true,
                true);
        rendererTested.setComputeLocalRange(false);
        rendererTested.setComputeLocalRange(true);

        assertNull(rendererTested.getPaintBarColor(null));

        final DefaultNumericAxis xAxis = new DefaultNumericAxis("time", "iso");
        xAxis.setTimeAxis(true);
        xAxis.setAutoRangeRounding(false);
        xAxis.setAutoRanging(false);
        Interval<Calendar> xrange = CalendarUtils.createByDateInterval("2020/11/18-2020/11/25");
        xAxis.set(xrange.from.getTime().getTime() / 1000.0, xrange.to.getTime().getTime() / 1000.0);

        final DefaultNumericAxis yAxis = new DefaultNumericAxis("price", "points");
        yAxis.setAutoRanging(false);

        // prepare chart structure
        chart = new XYChart(xAxis, yAxis);
        chart.getGridRenderer().setDrawOnTop(false);

        rendererTested.getDatasets().add(ohlcvDataSet);
        chart.getRenderers().clear();
        chart.getRenderers().add(rendererTested);

        // PaintBar extension usage
        rendererTested.setPaintBarMarker(d -> d.ohlcvItem != null ? Math.abs(d.ohlcvItem.getOpen() - d.ohlcvItem.getClose()) > 2.0 ? Color.MAGENTA : null : null);

        // Extension point usage
        rendererTested.addPaintAfterEp(data -> assertNotNull(data.gc));
        assertEquals(1, rendererTested.getPaintAfterEps().size());

        new FinancialColorSchemeConfig().applyTo(scheme, chart);

        stage.setScene(new Scene(chart, 800, 600));
        stage.show();
    }

    @TestFx
    public void categoryAxisTest() {
        final CategoryAxis xAxis = new CategoryAxis("time [iso]");
        xAxis.setTickLabelRotation(90);
        xAxis.setOverlapPolicy(AxisLabelOverlapPolicy.SKIP_ALT);
        ohlcvDataSet.setCategoryBased(true);

        chart.getAxes().add(0, xAxis);
        chart.layoutChildren();
    }

    @TestFx
    public void checkMinimalDimRequired() {
        rendererTested.getDatasets().clear();
        rendererTested.getDatasets().add(new AbstractDataSet<OhlcvDataSet>("testDim", 6) {
            @Override
            public double get(int dimIndex, int index) {
                return 0;
            }

            @Override
            public int getDataCount() {
                return 1;
            }

            @Override
            public DataSet set(DataSet other, boolean copy) {
                return null;
            }
        });
        var ref = new Object() {
            AssertionFailedError e = null;
        };
        rendererTested.addPaintAfterEp(data -> ref.e = new AssertionFailedError("The renderer method cannot be call, because dimensions are lower as required!"));
        chart.layoutChildren();
        if (ref.e != null) {
            throw ref.e;
        }
    }

    @Test
    public void testShortConstructor() {
        FootprintRendererAttributes footprintAttrs = FootprintRendererAttributes.getDefaultValues(schemes[0]);
        FootprintRenderer renderer = new FootprintRenderer(
                new FootprintRenderedAPIDummyAdapter(footprintAttrs));
        assertFalse(renderer.isPaintVolume());
        assertTrue(renderer.isPaintPoc());
        assertTrue(renderer.isPaintPullbackColumn());
    }

    @Test
    public void testLongConstructor() {
        FootprintRendererAttributes footprintAttrs = FootprintRendererAttributes.getDefaultValues(schemes[0]);
        FootprintRenderer renderer = new FootprintRenderer(
                new FootprintRenderedAPIDummyAdapter(footprintAttrs),
                true,
                true,
                true);
        assertTrue(renderer.isPaintVolume());
        assertTrue(renderer.isPaintPoc());
        assertTrue(renderer.isPaintPullbackColumn());

        renderer = new FootprintRenderer(
                new FootprintRenderedAPIDummyAdapter(footprintAttrs),
                false,
                false,
                false);
        assertFalse(renderer.isPaintVolume());
        assertFalse(renderer.isPaintPoc());
        assertFalse(renderer.isPaintPullbackColumn());
    }

    @Test
    public void noXyChartInstance() {
        assertThrows(InvalidParameterException.class, () -> rendererTested.render(null, new TestChart(), 0, null));
    }

    @Test
    void getThis() {
        assertEquals(FootprintRenderer.class, rendererTested.getThis().getClass());
    }
}
