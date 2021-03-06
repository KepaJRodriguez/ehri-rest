package eu.ehri.project.commands;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.sail.SailGraph;
import com.tinkerpop.blueprints.oupls.sail.pg.PropertyGraphSail;
import com.tinkerpop.frames.FramedGraph;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Option;

import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Export to RDF.
 *
 * NB: This current has a problem with TP 2.4.0 which
 * causes a crash on array properties. It seems to have
 * been fixed on TP 2.5.0-SNAPSHOT.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
public class RdfExport extends BaseCommand implements Command {

    public final static String NAME = "export-rdf";

    public final static String DEFAULT_FORMAT = "n-triples";

    @Override
    public String getHelp() {
        return "export low-level graph structure as RDF";
    }

    @Override
    public String getUsage() {
        String usage = "Usage: " + NAME + " <filename>\n\n" +
                "  Accepted formats are: \n\n";
        for (String fmt : SailGraph.formats.keySet()) {
            usage = usage + "  " + fmt + "\n";
        }
        usage = usage + "\n";
        return usage;
    }

    @Override
    protected void setCustomOptions() {
        options.addOption(new Option("f", true, "RDF format"));
    }

    @Override
    public int execWithOptions(FramedGraph<? extends TransactionalGraph> graph, CommandLine cmdLine) throws Exception {

        if (cmdLine.getArgList().size() < 1) {
            throw new MissingArgumentException("Output file path missing");
        }
        String fmt = cmdLine.getOptionValue("f", DEFAULT_FORMAT);

        System.out.println(fmt);

        PropertyGraphSail propertyGraphSail = new PropertyGraphSail(graph.getBaseGraph(), false);
        SailGraph sailGraph = new SailGraph(propertyGraphSail);
        OutputStream outputStream = new FileOutputStream((String)cmdLine.getArgList().get(0));
        try {
            sailGraph.saveRDF(outputStream, fmt);
        } finally {
            outputStream.close();
        }

        return 0;
    }
}
