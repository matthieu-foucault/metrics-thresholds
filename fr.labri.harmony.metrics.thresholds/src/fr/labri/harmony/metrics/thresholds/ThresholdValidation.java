package fr.labri.harmony.metrics.thresholds;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import fr.labri.harmony.analysis.metrics.Metric;
import fr.labri.harmony.core.analysis.AbstractPostProcessingAnalysis;
import fr.labri.harmony.core.config.model.AnalysisConfiguration;
import fr.labri.harmony.core.dao.Dao;
import fr.labri.harmony.core.log.HarmonyLogger;
import fr.labri.harmony.core.model.Data;
import fr.labri.harmony.core.model.Item;
import fr.labri.harmony.core.model.Source;

public class ThresholdValidation extends AbstractPostProcessingAnalysis {

	public ThresholdValidation() {
		super();
	}

	public ThresholdValidation(AnalysisConfiguration config, Dao dao, Properties properties) {
		super(config, dao, properties);
	}

	@Override
	public void runOn(Collection<Source> sources) {
		try {

			HashMap<String, ArrayList<Double>> metrics = new HashMap<>();

			for (Source source : sources) {
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
				FileWriter writer = new FileWriter(new File(config.getFoldersConfiguration().getOutFolder() + "/validation_large.csv"), true);
				Collections.sort(metric.getValue());
				writer.write(metric.getKey() + "\t");
				double numLow = 0;
			//	int[] thresholds = new int[] {20,27,28,44};
				Iterator<Double> it = metric.getValue().iterator();
				for (int threshold = 1; threshold < 50 ; threshold++) {
					while (it.hasNext() && it.next() <= threshold) {
						numLow++;
					}
					double ratio = numLow / metric.getValue().size();
					writer.write(ratio + "\t");
				}
				writer.write("\n");
				writer.close();
				HarmonyLogger.info("Done!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
