package com.eveningoutpost.dexdrip.ui.charts;

import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.UtilityModels.ColorCache;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.profileeditor.BasalProfile;
import com.eveningoutpost.dexdrip.xdrip;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import lecho.lib.hellocharts.formatter.AxisValueFormatter;
import lecho.lib.hellocharts.formatter.SimpleAxisValueFormatter;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Column;
import lecho.lib.hellocharts.model.ColumnChartData;
import lecho.lib.hellocharts.model.SubcolumnValue;
import lecho.lib.hellocharts.view.ColumnChartView;

import static com.eveningoutpost.dexdrip.UtilityModels.ColorCache.getCol;

// jamorham

public class BasalChart {

    // TODO sane y step types
    // TODO y axis recalculate in realtime
    // TODO disable double tap

    public static float CHART_EXTRA = 0.5f;
    private static final int segments = 24;

    public static ColumnChartData columnData(final String profileName) {
        final ColumnChartData columnChartData = new ColumnChartData(getColumns(profileName,getCol(ColorCache.X.color_basal_tbr)));;
        columnChartData.setBaseValue(-0.1f);
        columnChartData.setAxisXBottom(chartXAxis(columnChartData.getColumns().size()));
        return columnChartData;
    }

    public static float getTotalBasal(final ColumnChartData columnChartData) {
        float total = 0;
        for (final Column column : columnChartData.getColumns()) {
            total += column.getValues().get(0).getValue();
        }
        return total;
    }

    public static float getTotalImmutableBasal(final ColumnChartData columnChartData) {
        float total = 0;
        for (final Column column : columnChartData.getColumns()) {
            total += column.getValues().get(0).getImmutableOriginValue();
        }
        return total;
    }

    public static void refreshAxis(final ColumnChartView chart) {
        chart.getChartData().setAxisYRight(chartYAxis((float) chart.getMaximumViewport().top));
    }

    private static List<Column> getColumns(final String profileName, @ColorInt int color) {
        final List<Float> loaded = BasalProfile.load(profileName);

        final List<Column> columns = new ArrayList<>();
        for (int i = 0; i < segments; i++) {

            final List<SubcolumnValue> values = new ArrayList<>();

            float units;
            if (loaded != null && loaded.size() == segments) {
                units = loaded.get(i);
            } else {
                units = 0.5f;
            }

           // SubcolumnValue val = new SubcolumnValue(((float) ((int) (Math.random() * 10f))) / 10 + 0, color);
            SubcolumnValue val = new SubcolumnValue(units, color);

            setLabelForSubcolumn(val);
            values.add(val);
            // SubcolumnValue valBehind = new SubcolumnValue(val.getValue(), Color.GREEN);
            //setLabelForSubcolumn(val);
            // values.add(valBehind);

            final Column column = new Column(values);
            column.setHasImmutable(true);
            column.setHasLabels(true);
            column.setHasLabelsOnlyForSelected(true);
            columns.add(column);
        }

        return columns;
    }

    public static void setLabelForSubcolumn(final SubcolumnValue val) {
        val.setLabel(JoH.qs(val.getValue(), 2));
    }

    static private Axis chartXAxis(int size) {

        final Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        xAxis.setHasTiltedLabels(true);
        xAxis.setTiltAngle(-90f);
        xAxis.setMaxLabelChars(7);

        SimpleDateFormat sdf = new SimpleDateFormat(DateFormat.is24HourFormat(xdrip.getAppContext()) ? "HH:mm" : "a h:mm", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        DateFormatSymbols symbols = new DateFormatSymbols(Locale.getDefault());
        // OVERRIDE SOME symbols WHILE RETAINING OTHERS
        symbols.setAmPmStrings(new String[] { "a", "p" });
        sdf.setDateFormatSymbols(symbols);

        final GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(JoH.tsl());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        xAxis.setAutoGenerated(false);

        final List<AxisValue> axisValues = new ArrayList<>();
        final int step = size / segments;
        final long dayStartMs = calendar.getTimeInMillis();
        final long increment = Constants.DAY_IN_MS / segments;
        for (int i = 0; i < size; i = i + step) {
            calendar.setTimeInMillis(dayStartMs + i*increment);
            axisValues.add(new AxisValue(((float)i / step)+((float)step/6),  sdf.format(calendar.getTimeInMillis()).toCharArray()));
        }
        xAxis.setValues(axisValues);
        return xAxis;
    }

    public static float getMaxYvalue(ColumnChartData lineData) {
        float max_height = 0.2f;
        for (Column col : lineData.getColumns()) {
            final SubcolumnValue sub = col.getValues().get(0);
            final float val = (float) Math.max(sub.getValue(), sub.getImmutableOriginValue());
            if (val > max_height) {
                max_height = val;
            }
        }
        return max_height;
    }

    // TODO recalculate axis on every change
    @NonNull
    static public Axis chartYAxis(float max_height) {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(false);
        yAxis.setHasLines(true);

        //max_height = max_height * 3;
        final float yStep = chooseClosestStep(max_height / 20f);

        // rounding???

        final List<AxisValue> axisValues = new ArrayList<>();

        // TODO autoscale this - rebuild on data change??
        for (float j = 0; j <= max_height + yStep; j += yStep) {
            axisValues.add(new AxisValue(j));
        }
        yAxis.setValues(axisValues);
        // yAxis.setHasLines(true);
        yAxis.setMaxLabelChars(5);
        yAxis.setInside(false);
        // yAxis.setTextSize(axisTextSize);
        AxisValueFormatter formatter = new SimpleAxisValueFormatter(2);
        yAxis.setFormatter(formatter);
        return yAxis;
    }

    private static final float[] STEPS = {0.01f, 0.02f, 0.05f, 0.10f, 0.20f, 0.50f, 1.0f, 2.0f, 5.0f, 10.0f};

    static float chooseClosestStep(final float raw) {
        float best = 1000f;
        float choice = 0.01f;
        for (final float f : STEPS) {
            final float diff = Math.abs(f - raw);
            if (diff < best) {
                best = diff;
                choice = f;
            } else {
                break;
            }
        }
        return choice;
    }
}
