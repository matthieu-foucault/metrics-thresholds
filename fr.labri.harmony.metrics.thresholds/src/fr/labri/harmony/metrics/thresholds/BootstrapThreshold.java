package fr.labri.harmony.metrics.thresholds;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import rcaller.RCaller;
import rcaller.RCode;
import fr.labri.harmony.analysis.metrics.Metric;
import fr.labri.harmony.core.analysis.AbstractPostProcessingAnalysis;
import fr.labri.harmony.core.config.model.AnalysisConfiguration;
import fr.labri.harmony.core.dao.Dao;
import fr.labri.harmony.core.log.HarmonyLogger;
import fr.labri.harmony.core.model.Data;
import fr.labri.harmony.core.model.Item;
import fr.labri.harmony.core.model.Source;

public class BootstrapThreshold extends AbstractPostProcessingAnalysis {

	private final static String R_SCRIPT_PATH = "r-script-path";
	private String rScriptPath;

	public BootstrapThreshold() {
		super();
	}

	public BootstrapThreshold(AnalysisConfiguration config, Dao dao, Properties properties) {
		super(config, dao, properties);
		Map<String, Object> opts = config.getOptions();
		if (opts != null) {
			rScriptPath = (String) opts.get(R_SCRIPT_PATH);
		}
	}

	@Override
	public void runOn(Collection<Source> sources) {
		try {

			HashMap<String, ArrayList<Double>> metrics = new HashMap<>();
			String configurationFile = null;
			for (Source source : sources) {
				if (configurationFile == null) configurationFile = source.getConfig().getConfigurationFileName();
				@SuppressWarnings("unchecked")
				Collection<String> sampledItems = (Collection<String>) source.getConfig().getOptions().get("sampled-items");
				if (sampledItems != null) {
					for (String nativeId : sampledItems) {
						Item item = dao.getItem(source, nativeId);
						List<Metric> itemMetrics = dao.getDataList("metrics", Metric.class, Data.ITEM, item.getId());
						for (Metric metric : itemMetrics) {
							String metricName = metric.getName();
							ArrayList<Double> metricValues = metrics.get(metricName);
							if (metricValues == null) metricValues = new ArrayList<>();
							metricValues.add(metric.getValue());
							metrics.put(metricName, metricValues);
						}
					}
				} else {
					for (Item item : source.getItems()) {
						List<Metric> itemMetrics = dao.getDataList("metrics", Metric.class, Data.ITEM, item.getId());
						for (Metric metric : itemMetrics) {
							String metricName = metric.getName();
							ArrayList<Double> metricValues = metrics.get(metricName);
							if (metricValues == null) metricValues = new ArrayList<>();
							metricValues.add(metric.getValue());
							metrics.put(metricName, metricValues);
						}
					}
				}
			}

			for (Map.Entry<String, ArrayList<Double>> metric : metrics.entrySet()) {
				double startTime = System.currentTimeMillis();
				double[] values = new double[metric.getValue().size()];
				for (int i = 0; i < metric.getValue().size(); i++) {
					values[i] = metric.getValue().get(i);
				}
				RCaller caller = new RCaller();
				caller.setRscriptExecutable(new File(rScriptPath).getAbsolutePath());
				RCode code = new RCode();
				code.clear();
				code.addRCode("library(boot)");

				code.addDoubleArray("x", values);

				code.addRCode("quant80 <- function(dat, idx) {\n" + "return (quantile(dat[idx], c(0.9))[1])\n" + "}");
				// code.addRCode("b <- quant80(x)");
				code.addRCode("bootstrap <- boot(x, statistic=quant80, stype=\"i\", R=10000)");
				code.addRCode("b <- boot.ci(bootstrap, type=\"bca\")$bca");
				caller.setRCode(code);
				// caller.redirectROutputToConsole();
				try {
					caller.runAndReturnResult("b");
					double[] bootstrap = caller.getParser().getAsDoubleArray("b");

					double stopTime = System.currentTimeMillis();
					HarmonyLogger.info("Bootstrap Time : " + (stopTime - startTime) / 1000 + "s");

					// save data

					String outFile = (String) config.getOptions().get("out-file");
					FileWriter writer = new FileWriter(new File(config.getFoldersConfiguration().getOutFolder() + File.separator + outFile), true);

					writer.write(configurationFile + "\t" + metric.getKey() + "\t" + bootstrap[3] + "\t" + bootstrap[4] + "\t" + (stopTime - startTime) / 1000 + "\n");
					writer.close();
				} catch (Exception e) {
					double stopTime = System.currentTimeMillis();
					HarmonyLogger.info("Bootstrap Time : " + (stopTime - startTime) / 1000 + "s");

					// save data

					String outFile = (String) config.getOptions().get("out-file");
					FileWriter writer = new FileWriter(new File(config.getFoldersConfiguration().getOutFolder() + File.separator + outFile), true);

					writer.write(configurationFile + "\t" + metric.getKey() + "\tNA\tNA\t" + (stopTime - startTime) / 1000 + "\n");
					writer.close();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
