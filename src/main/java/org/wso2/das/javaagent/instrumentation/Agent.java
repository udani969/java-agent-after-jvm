package org.wso2.das.javaagent.instrumentation;

import org.json.simple.parser.ParseException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAgentConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointAuthenticationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointConfigurationException;
import org.wso2.carbon.databridge.agent.exception.DataEndpointException;
import org.wso2.carbon.databridge.commons.exception.AuthenticationException;
import org.wso2.carbon.databridge.commons.exception.TransportException;
import org.wso2.das.javaagent.schema.AgentConnection;
import org.wso2.das.javaagent.schema.InstrumentationAgent;
import org.wso2.das.javaagent.schema.InstrumentationClass;
import org.wso2.das.javaagent.schema.Scenario;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.List;

public class Agent {

    public static void premain(String agentArgs, Instrumentation instrumentation) throws JAXBException,
            ClassNotFoundException, UnmodifiableClassException, DataEndpointException,
            IOException, DataEndpointConfigurationException,
            DataEndpointAuthenticationException, DataEndpointAgentConfigurationException,
            TransportException, AuthenticationException, ParseException {

        System.out.println("Agent Premain Start");
        instrumentation.addTransformer(new InstrumentationClassTransformer(),
                instrumentation.isRetransformClassesSupported());
//        System.out.println(AgentPublisher.CARBON_HOME +"/repository/conf/javaagent/inst-agent-config.xml");
//        File file = new File("inst-agent-config.xml");
//        File file = new File("/home/udani/Documents/WSO2_files/product-bam/modules/distribution/" +
//                "target/wso2das-3.0.1-SNAPSHOT/repository/conf/javaagent/inst-agent-config.xml");
        File file = new File("/Downloads/wso2das-3.0.0/repository/conf/javaagent/inst-agent-config.xml");
//        /Downloads/wso2das-3.0.0
        JAXBContext jaxbContext = JAXBContext.newInstance(InstrumentationAgent.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        InstrumentationAgent agent = (InstrumentationAgent) jaxbUnmarshaller.unmarshal(file);

        AgentConnection agentConnection = agent.getAgentConnection();

        AgentPublisher publisherObj = new AgentPublisher(agentConnection);

        List<Scenario> scenarios = agent.getScenarios();
        for (Scenario scenario : scenarios) {
            List<InstrumentationClass> instrumentationClasses = scenario.getinstrumentationClasses();
            for (InstrumentationClass instrumentationClass : instrumentationClasses){
                Class currentClass = ClassLoader.getSystemClassLoader().loadClass(
                        instrumentationClass.getClassName());
                    SchemaClass currentClassWithDetails = new SchemaClass(
                            scenario.getScenarioName(), currentClass, instrumentationClass);
                    InstrumentationClassTransformer.transformMe.add(currentClassWithDetails);
                    instrumentation.retransformClasses(currentClass);
                    InstrumentationClassTransformer.transformMe.remove(currentClassWithDetails);
            }
        }

//        if(!AgentPublisher.getArbitraryFields().isEmpty()){
//            AgentPublisher.updateCurrentSchema(
//                    AgentPublisher.generateConnectionURL(agentConnection),
//                    agentConnection.getUsername(),agentConnection.getPassword(),
//                    AgentPublisher.getArbitraryFields());
//        }

        System.out.println("Instrumentation complete");

    }
}
