package org.demonsoft.spatialkappa.tools;

import static org.demonsoft.spatialkappa.model.Location.NOT_LOCATED;
import static org.demonsoft.spatialkappa.model.Utils.getChannel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.demonsoft.spatialkappa.model.Agent;
import org.demonsoft.spatialkappa.model.AgentDeclaration;
import org.demonsoft.spatialkappa.model.AgentSite;
import org.demonsoft.spatialkappa.model.AggregateSite;
import org.demonsoft.spatialkappa.model.Channel;
import org.demonsoft.spatialkappa.model.ChannelConstraint;
import org.demonsoft.spatialkappa.model.Compartment;
import org.demonsoft.spatialkappa.model.Complex;
import org.demonsoft.spatialkappa.model.Complex.MappingInstance;
import org.demonsoft.spatialkappa.model.IKappaModel;
import org.demonsoft.spatialkappa.model.InitialValue;
import org.demonsoft.spatialkappa.model.KappaModel;
import org.demonsoft.spatialkappa.model.Location;
import org.demonsoft.spatialkappa.model.Transition;
import org.demonsoft.spatialkappa.model.Utils;
import org.demonsoft.spatialkappa.model.Variable;
import org.demonsoft.spatialkappa.model.Variable.Type;

// Conversion of any moderately complex Spatial Kappa model can produce huge 
// number of potential rules, so maintaining this translator is not worth
// the effort at the moment
@Deprecated 
public class SpatialTranslator {

    private final IKappaModel kappaModel;

    public SpatialTranslator(File inputFile) throws Exception {
        if (inputFile == null) {
            throw new NullPointerException();
        }
        kappaModel = Utils.createKappaModel(inputFile);
    }

    SpatialTranslator(String input) throws Exception {
        if (input == null) {
            throw new NullPointerException();
        }
        kappaModel = Utils.createKappaModel(new ByteArrayInputStream(input.getBytes()));
    }

    SpatialTranslator(IKappaModel kappaModel) {
        if (kappaModel == null) {
            throw new NullPointerException();
        }
        this.kappaModel = kappaModel;
    }

    public String translateToKappa() {
        StringBuilder builder = new StringBuilder();

        String aggregateLocationState = getAgentDeclLocationState(kappaModel.getCompartments());

        List<String> agentNames = new ArrayList<String>(kappaModel.getAgentDeclarationMap().keySet());
        if (agentNames.size() > 0) {
            builder.append("### AGENTS\n");
            Collections.sort(agentNames);
            for (String agentName : agentNames) {
                builder.append(getKappaString(kappaModel.getAgentDeclarationMap().get(agentName), aggregateLocationState));
            }
            builder.append("\n");
        }
        
        // TODO - restrict diffusion agents to unlinked complexes
        if (kappaModel.getTransitions().size() > 0) {
            builder.append("### RULES\n");
            for (Transition transition : kappaModel.getTransitions()) {
                builder.append(getKappaString(transition));
            }
            builder.append("\n");
        }
        
        if (kappaModel.getInitialValues().size() > 0) {
            builder.append("### INITIAL VALUES\n");
            for (InitialValue initialValue : kappaModel.getInitialValues()) {
                builder.append(getKappaString(initialValue));
            }
            builder.append("\n");
        }
        
        List<String> variableNames = new ArrayList<String>(kappaModel.getOrderedVariableNames());
        if (variableNames.size() > 0) {
            builder.append("### VARIABLES\n");
            for (String variableName : variableNames) {
                Variable variable = kappaModel.getVariables().get(variableName);
                if (variable.type != Type.TRANSITION_LABEL) {
                    builder.append(getKappaString(variable));
                }
            }
            builder.append("\n");
        }

        if (kappaModel.getPlottedVariables().size() > 0) {
            builder.append("### PLOTS\n");
            for (String plotName : kappaModel.getPlottedVariables()) {
                builder.append("%plot: '").append(plotName).append("'\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String getAgentDeclLocationState(List<Compartment> compartments) {
        if (compartments == null || compartments.size() == 0) {
            return "";
        }

        int maxDimensions = 0;
        List<String> compartmentNames = new ArrayList<String>();
        for (Compartment compartment : compartments) {
            compartmentNames.add(compartment.getName());
            if (compartment.getDimensions().length > maxDimensions) {
                maxDimensions = compartment.getDimensions().length;
            }
        }

        int[] maxDimensionSizes = new int[maxDimensions];
        for (Compartment compartment : compartments) {
            for (int index = 0; index < compartment.getDimensions().length; index++) {
                if (compartment.getDimensions()[index] > maxDimensionSizes[index]) {
                    maxDimensionSizes[index] = compartment.getDimensions()[index];
                }
            }
        }

        StringBuilder result = new StringBuilder();

        result.append("loc");
        Collections.sort(compartmentNames);
        for (String compartmentName : compartmentNames) {
            result.append("~").append(compartmentName);
        }

        for (int index = 0; index < maxDimensions; index++) {
            result.append(",loc_index_").append(index + 1);
            for (int stateIndex = 0; stateIndex < maxDimensionSizes[index]; stateIndex++) {
                result.append("~").append(stateIndex);
            }
        }

        return result.toString();
    }

    private Object getKappaString(AgentDeclaration agent, String agentDeclLocationState) {
        StringBuilder builder = new StringBuilder();
        builder.append("%agent: ").append(agent.getName()).append("(");

        boolean firstElement = true;
        for (AggregateSite site : agent.getSites()) {
            if (!firstElement) {
                builder.append(",");
            }
            else {
                firstElement = false;
            }
            builder.append(site.getName());
            for (String state : site.getStates()) {
                builder.append("~").append(state);
            }
        }
        if (agent.getSites().size() > 0 && agentDeclLocationState.length() > 0) {
            builder.append(",");
        }
        builder.append(agentDeclLocationState).append(")\n");
        return builder.toString();
    }

    private List<Agent> getAggregateAgents(Map<String, AgentDeclaration> aggregateAgentMap) {
        List<Agent> result = new ArrayList<Agent>();
        for (String agentName : aggregateAgentMap.keySet()) {
            result.add(new Agent(agentName));
        }
        Collections.sort(result, new Comparator<Agent>() {
            public int compare(Agent o1, Agent o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return result;
    }

    private String getAgentKappaString(List<Agent> agents) {
        return getAgentKappaString(agents, "");
    }

    private String getAgentKappaString(List<Agent> agents, String stateSuffix) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Agent agent : agents) {
            if (!first) {
                builder.append(",");
            }
            if (agent.location != NOT_LOCATED) {
                builder.append(agent.toString(getKappaString(agent.location), true));
            }
            else {
                builder.append(agent.toString(stateSuffix, true));
            }
            first = false;
        }
        return builder.toString();
    }

    private String getKappaString(Complex complex, String agentStateSuffix) {
        return getAgentKappaString(complex.agents, agentStateSuffix);
    }

    private String getKappaString(Location location) {
        int dimensionCount = 0;
        if (location != null && location != NOT_LOCATED) {
            dimensionCount = location.getDimensionCount();
        }
        return getKappaString(location, dimensionCount);
    }

    String getKappaString(Location location, int dimensionCount) {
        if (location == NOT_LOCATED) {
            return "";
        }
        int usedDimensions = 0;
        if (location.getDimensionCount() > 0) {
            usedDimensions = location.getDimensionCount();
        }
        if (usedDimensions > dimensionCount) {
            throw new IllegalArgumentException();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("loc~").append(location.getName());
        for (int index = 0; index < usedDimensions; index++) {
            builder.append(",loc_index_").append(index + 1).append("~");
            if (location.isConcreteLocation()) {
                builder.append(location.getFixedIndices()[index]);
            }
            else {
                builder.append(location.getIndices()[index].evaluateIndex());
            }
        }
        for (int index = usedDimensions; index < dimensionCount; index++) {
            builder.append(",loc_index_").append(index + 1).append("~0");
        }

        return builder.toString();
    }

    private String getKappaString(Variable variable) {
        StringBuilder builder = new StringBuilder();
        switch (variable.type) {
        case KAPPA_EXPRESSION:
            if (!hasLinksWithChannels(variable.complex)) {
                builder.append("%var: '").append(variable.label).append("'");
                builder.append(" ").append(getKappaString(variable.complex, getKappaString(variable.location)));
                builder.append("\n");
            }
            else {
                List<MappingInstance> mappings = variable.complex.getMappingInstances(
                        kappaModel.getCompartments(), kappaModel.getChannels());
                if (mappings.size() == 1) {
                    builder.append("%var: '").append(variable.label).append("'");
                    builder.append(" ").append(getAgentKappaString(mappings.get(0).locatedAgents));
                    builder.append("\n");
                }
                else if (mappings.size() > 1) {
                    int index = 0;
                    StringBuilder totalVariableExpression = new StringBuilder();
                    for (MappingInstance mapping : mappings) {
                        String mappingVariableName = variable.label + "-" + (++index);
                        builder.append("%var: '").append(mappingVariableName).append("'");
                        builder.append(" ").append(getAgentKappaString(mapping.locatedAgents));
                        builder.append("\n");
                        
                        if (index > 1) {
                            totalVariableExpression.append(" + ");
                        }
                        totalVariableExpression.append("'").append(mappingVariableName).append("'");
                    }
                    
                    builder.append("%var: '").append(variable.label).append("'");
                    builder.append(" ").append(totalVariableExpression.toString());
                    builder.append("\n");
                }
            }
            break;
        case VARIABLE_EXPRESSION:
            builder.append("%var: '").append(variable.label).append("'");
            builder.append(" ").append(variable.expression.toString());
            builder.append("\n");
            break;
        case TRANSITION_LABEL:
            // Do nothing
            break;
        }
        return builder.toString();
    }

    boolean hasLinksWithChannels(Complex complex) {
        if (complex == null) {
            throw new NullPointerException();
        }
        return hasLinksWithChannels(complex.agents);
    }

    boolean hasLinksWithChannels(List<Agent> agents) {
        if (agents == null) {
            throw new NullPointerException();
        }
        for (Agent agent : agents) {
            for (AgentSite site : agent.getSites()) {
                if (site != null && site.getChannel() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getKappaString(Transition transition) {
        StringBuilder builder = new StringBuilder();
        if (transition.channelName != null) {
            Channel channel = getChannel(kappaModel.getChannels(), transition.channelName);
            int labelSuffix = 1;
            String[][] stateSuffixPairs = getLinkStateSuffixPairs(channel, kappaModel.getCompartments());
            List<Agent> agents = transition.leftAgents;
            if (agents.size() > 0) {
                labelSuffix = writeAgents(builder, stateSuffixPairs, transition, agents, labelSuffix,
                        kappaModel.getAgentDeclarationMap(), true);
            }
            else {
                agents = getAggregateAgents(kappaModel.getAgentDeclarationMap());
                List<Agent> currentAgents = new ArrayList<Agent>();
                for (Agent agent : agents) {
                    currentAgents.clear();
                    currentAgents.add(agent);
                    labelSuffix = writeAgents(builder, stateSuffixPairs, transition, currentAgents,
                            labelSuffix, kappaModel.getAgentDeclarationMap(), true);
                }
            }
        }
        else {
            List<Transition> locatedTransitions = 
                    ((KappaModel) kappaModel).getValidLocatedTransitions(transition, kappaModel.getCompartments(), kappaModel.getChannels());
    
            for (Transition locatedTransition : locatedTransitions) {
                if (locatedTransition.label != null) {
                    builder.append("'").append(locatedTransition.label).append("' ");
                }
                if (locatedTransition.leftAgents.size() > 0) {
                    builder.append(getAgentKappaString(locatedTransition.leftAgents)).append(" ");
                }
                builder.append("-> ");
                if (locatedTransition.rightAgents.size() > 0) {
                    builder.append(getAgentKappaString(locatedTransition.rightAgents)).append(" ");
                }
                builder.append("@ ").append(locatedTransition.getRate().toString()).append("\n");
            }
            
        }
        return builder.toString();
    }

    
    private int writeAgents(StringBuilder builder, String[][] stateSuffixPairs, Transition transition,
            List<Agent> agents, int startLabelSuffix, Map<String, AgentDeclaration> aggregateAgentMap, boolean forceSuffix) {

        int labelSuffix = startLabelSuffix;
        List<Agent> isolatedAgents = getIsolatedAgents(agents, aggregateAgentMap);

        for (int index = 0; index < stateSuffixPairs.length; index++) {
            if (transition.label != null) {
                builder.append("'").append(transition.label);
                if (stateSuffixPairs.length > 1 || forceSuffix) {
                    builder.append("-").append(labelSuffix++);
                }
                builder.append("' ");
            }
            String leftSuffix = stateSuffixPairs[index][0];
            String rightSuffix = stateSuffixPairs[index][1];
            builder.append(getAgentKappaString(isolatedAgents, leftSuffix));
            builder.append(" -> ");
            builder.append(getAgentKappaString(isolatedAgents, rightSuffix)).append(" @ ");
            builder.append(transition.getRate().toString());
            builder.append("\n");
        }
        return labelSuffix;
    }

    private List<Agent> getIsolatedAgents(List<Agent> agents, Map<String, AgentDeclaration> aggregateAgentMap) {
        List<Agent> result = new ArrayList<Agent>();
        for (Agent agent : agents) {
            List<AgentSite> isolatedSites = new ArrayList<AgentSite>();
            isolatedSites.addAll(agent.getSites()); // Sites are cloned - no
                                                    // problem here

            AgentDeclaration aggregateAgent = aggregateAgentMap.get(agent.name);
            for (AggregateSite site : aggregateAgent.getSites()) {
                if (site.getLinks().size() > 0 && agent.getSite(site.getName()) == null) {
                    isolatedSites.add(new AgentSite(site.getName(), null, null));
                }
            }
            result.add(new Agent(agent.name, isolatedSites));
        }
        return result;
    }

    private String getKappaString(InitialValue initialValue) {
        StringBuilder builder = new StringBuilder();
        int quantity = initialValue.quantity;
        
        if (initialValue.reference != null) {
            Variable target = kappaModel.getVariables().get(initialValue.reference.variableName);
            quantity = target.evaluate(kappaModel);
        }
        
        for (Complex complex : initialValue.complexes) {
            List<MappingInstance> mappings = complex.getMappingInstances(kappaModel.getCompartments(), kappaModel.getChannels());
            int totalCellCount = mappings.size();
            if (totalCellCount > 0) {
                int baseValue = quantity / totalCellCount;
                int remainder = quantity % totalCellCount;
    
                int index = 0;
    
                for (MappingInstance mapping : mappings) {
                    builder.append("%init: ").append(baseValue + (index++ < remainder ? 1 : 0)).append(" ");
                    builder.append(getAgentKappaString(mapping.locatedAgents));
                    builder.append("\n");
                }
            }
        }
        
        return builder.toString();
    }

    String[][] getLinkStateSuffixPairs(Channel link, List<Compartment> compartments) {
        List<ChannelConstraint> references = link.getCellReferencePairs(compartments);
        String[][] result = new String[references.size()][2];
        int maxDimensions = 0;
        for (Compartment compartment : compartments) {
            if (compartment.getDimensions() != null && compartment.getDimensions().length > maxDimensions) {
                maxDimensions = compartment.getDimensions().length;
            }
        }
        for (int index = 0; index < references.size(); index++) {
            result[index][0] = getKappaString(references.get(index).sourceLocation, maxDimensions);
            result[index][1] = getKappaString(references.get(index).targetConstraint, maxDimensions);
        }
        return result;
    }

    
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("SpatialTranslator version " + Version.VERSION);
            System.err.println("Usage: SpatialTranslator <input file path> [<output file path>]");
            return;
        }

        String result = new SpatialTranslator(new File(args[0])).translateToKappa();
        if (args.length == 1) {
            System.out.println(result);
        }
        else {
            FileUtils.writeStringToFile(new File(args[1]), result);
            System.out.println("Result written to " + args[1]);
        }
    }

}
