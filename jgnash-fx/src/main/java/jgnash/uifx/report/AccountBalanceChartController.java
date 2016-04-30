/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2016 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.uifx.report;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;

import jgnash.engine.Account;
import jgnash.engine.CurrencyNode;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.report.ReportPeriod;
import jgnash.report.ReportPeriodUtils;
import jgnash.text.CommodityFormat;
import jgnash.time.DateUtils;
import jgnash.uifx.Options;
import jgnash.uifx.control.AccountComboBox;
import jgnash.uifx.control.DatePickerEx;
import jgnash.uifx.util.InjectFXML;

/**
 * Periodic Account Balance Bar Chart
 *
 * @author Craig Cavanaugh
 */
public class AccountBalanceChartController {

    private static final String CHART_CSS = "jgnash/skin/incomeExpenseBarChart.css";

    private static final String REPORT_PERIOD = "reportPeriod";

    private static final int BAR_GAP = 1;

    private static final int CATEGORY_GAP = 20;

    @InjectFXML
    private final ObjectProperty<Scene> parentProperty = new SimpleObjectProperty<>();

    @FXML
    private AccountComboBox accountComboBox;

    @FXML
    private ComboBox<ReportPeriod> periodComboBox;

    @FXML
    private BarChart<String, Number> barChart;

    @FXML
    private DatePickerEx startDatePicker;

    @FXML
    private DatePickerEx endDatePicker;

    @FXML
    private CheckBox includeSubAccounts;

    @FXML
    private ResourceBundle resources;

    private CurrencyNode defaultCurrency;

    private NumberFormat numberFormat;

    @FXML
    public void initialize() {

        accountComboBox.setPredicate(AccountComboBox.getShowAllPredicate());

        final Preferences preferences = Preferences.userNodeForPackage(AccountBalanceChartController.class);

        final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        Objects.requireNonNull(engine);

        periodComboBox.getItems().addAll(ReportPeriod.MONTHLY, ReportPeriod.QUARTERLY);
        periodComboBox.setValue(ReportPeriod.values()[preferences.getInt(REPORT_PERIOD,
                ReportPeriod.MONTHLY.ordinal())]);

        defaultCurrency = engine.getDefaultCurrency();
        numberFormat = CommodityFormat.getFullNumberFormat(defaultCurrency);

        barChart.getStylesheets().addAll(CHART_CSS);
        barChart.getYAxis().setLabel(defaultCurrency.getSymbol());
        barChart.barGapProperty().set(BAR_GAP);
        barChart.setCategoryGap(CATEGORY_GAP);
        barChart.setLegendVisible(false);
        barChart.getXAxis().setLabel(resources.getString("Column.Period"));

        // Respect animation preference
        barChart.animatedProperty().setValue(Options.animationsEnabledProperty().get());

        startDatePicker.setValue(DateUtils.getFirstDayOfTheMonth(endDatePicker.getValue().minusMonths(11)));

        includeSubAccounts.selectedProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(AccountBalanceChartController.this::updateChart);
        });

        accountComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                defaultCurrency = newValue.getCurrencyNode();
                numberFormat = CommodityFormat.getFullNumberFormat(defaultCurrency);

                Platform.runLater(AccountBalanceChartController.this::updateChart);
            }
        });

        final ChangeListener<Object> listener = (observable, oldValue, newValue) -> {
            if (newValue != null) {
                Platform.runLater(AccountBalanceChartController.this::updateChart);
            }
        };

        startDatePicker.valueProperty().addListener(listener);
        endDatePicker.valueProperty().addListener(listener);

        periodComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            preferences.putInt(REPORT_PERIOD, newValue.ordinal());
            Platform.runLater(this::updateChart);
        });

        // Push the initial load to the end of the platform thread for better startup and a nicer visual effect
        Platform.runLater(this::updateChart);
    }

    private void updateChart() {
        final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        Objects.requireNonNull(engine);

        final Account account = accountComboBox.getValue();

        barChart.getData().clear();

        final List<ReportPeriodUtils.Descriptor> descriptors = ReportPeriodUtils.getDescriptors(
                periodComboBox.getValue(), startDatePicker.getValue(), endDatePicker.getValue());

        // Income Series
        final XYChart.Series<String, Number> series = new XYChart.Series<>();
        barChart.getData().add(series);

        for (final ReportPeriodUtils.Descriptor descriptor : descriptors) {
            final BigDecimal income;

            if (!includeSubAccounts.isSelected()) {
                income = account.getBalance(descriptor.getStartDate(), descriptor.getEndDate());
            } else {
                income = account.getTreeBalance(descriptor.getStartDate(), descriptor.getEndDate(),
                        account.getCurrencyNode());
            }

            series.getData().add(new XYChart.Data<>(descriptor.getLabel(), income));
        }

        for (final XYChart.Data<String, Number> data : series.getData()) {
            Tooltip.install(data.getNode(), new Tooltip(numberFormat.format(data.getYValue())));
        }

        barChart.getYAxis().setLabel(resources.getString("Column.Balance") + " : " + defaultCurrency.getSymbol());
    }

    @FXML
    private void handleSaveAction() {
        ChartUtilities.saveChart(barChart);
    }

    @FXML
    private void handleCopyToClipboard() {
        ChartUtilities.copyToClipboard(barChart);
    }

    @FXML
    private void handlePrintAction() {
        ChartUtilities.printChart(barChart);
    }

    @FXML
    private void handleCloseAction() {
        ((Stage) parentProperty.get().getWindow()).close();
    }
}