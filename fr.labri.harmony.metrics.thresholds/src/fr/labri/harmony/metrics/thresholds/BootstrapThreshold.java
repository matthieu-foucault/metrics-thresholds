package fr.labri.harmony.metrics.thresholds;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import rcaller.RCaller;
import rcaller.RCode;
import fr.labri.harmony.core.analysis.AbstractPostProcessingAnalysis;
import fr.labri.harmony.core.config.model.AnalysisConfiguration;
import fr.labri.harmony.core.dao.Dao;
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
			RCaller caller = new RCaller();
			caller.setRscriptExecutable(new File(rScriptPath).getAbsolutePath());

			RCode code = new RCode();

			code.clear();

			double[] numbers = new double[] { 1, 4, 3, 5, 6, 10 };

			code.addDoubleArray("x", numbers);
			File file = code.startPlot();
			System.out.println("Plot will be saved to : " + file);
			code.addRCode("plot(x)");
			code.endPlot();

			caller.setRCode(code);
			System.out.println(code.getCode().toString());


			caller.runOnly();
			code.showPlot(file);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
