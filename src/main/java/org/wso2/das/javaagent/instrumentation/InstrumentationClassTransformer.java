package org.wso2.das.javaagent.instrumentation;

import javassist.*;
import org.wso2.das.javaagent.schema.InsertAt;
import org.wso2.das.javaagent.schema.InstrumentationMethod;
import org.wso2.das.javaagent.schema.ParameterName;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstrumentationClassTransformer implements ClassFileTransformer {
    public static List<SchemaClass> transformMe = new ArrayList<SchemaClass>();

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        byte[] transformedBytes = null;
        for (SchemaClass clazz : transformMe){
            if(clazz.getClazz().equals(classBeingRedefined)){
                ClassPool classPool = ClassPool.getDefault();
                try {
                    CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                    CtMethod[] methods = ctClass.getDeclaredMethods();

                    List<InstrumentationMethod> instrumentationMethods =
                            clazz.getInstrumentationClass().getinstrumentationMethods();
                        for(InstrumentationMethod instrumentationMethod : instrumentationMethods){
                            for(CtMethod method : methods){
                                if(method.getName().equals(instrumentationMethod.getMethodName()) &&
                                        method.getSignature().equals(instrumentationMethod.getMethodSignature())){
                                    instrumentMethod(createPayloadData(clazz.getScenarioName(),
                                                    clazz.getInstrumentationClass().getClassName(),
                                                    method.getName()), instrumentationMethod, method);
                                }
                            }
                        }

                    transformedBytes = ctClass.toBytecode();
                    ctClass.detach();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return transformedBytes;
    }


    public void instrumentMethod(String payloadData, InstrumentationMethod instrumentationMethod,
                                 CtMethod method) throws NotFoundException, CannotCompileException {
        createInsertBefore(payloadData, method, instrumentationMethod.getinsertBefore());

        List<InsertAt> insertAts = instrumentationMethod.getInsertAts();
        if(insertAts!=null && !insertAts.isEmpty()){
            for(InsertAt insertAt : insertAts){
                List<ParameterName> parameterNames = insertAt.getParameterNames();
                Map<String,String> parameterMap = new HashMap<String,String>();
                if(!parameterNames.isEmpty()){
                    for(ParameterName parameterName : parameterNames){
                        /*
                         * When setting setting the schema of the table we have to add a '_'
                         * before each table name. But when publishing data in map, use key name
                         * given in configuration file without '_'
                         */
                        AgentPublisher.setArbitraryFields("_"+parameterName.getKey());
                        parameterMap.put(parameterName.getKey(), parameterName.getParameterValue());
                    }

                    createInsertAt(payloadData, method, insertAt.getLineNo(), parameterMap);
                    parameterMap.clear();
                }
            }
        }
        createInsertAfter(payloadData, method, instrumentationMethod.getInsertAfter());
    }

    public void createInsertAt(String payloadData, CtMethod method, int lineNo,
                               Map<String,String> parameterMap)
            throws CannotCompileException {
        StringBuilder atBuilder = new StringBuilder();
        atBuilder.append("java.util.Map/*<String,String>*/ insertAtMap"+lineNo+" " +
                "= new java.util.HashMap/*<String,String>*/();");

        for (Map.Entry<String,String> entry : parameterMap.entrySet()) {
            atBuilder.append("insertAtMap"+lineNo+".put(\"" + entry.getKey() + "\","
                    + entry.getValue() + ");");
        }

        atBuilder.append("org.wso2.das.javaagent.instrumentation.AgentPublisher.publishEvents(" +
                "System.currentTimeMillis(),\"" + payloadData + ":line "+lineNo+": \",insertAtMap" + lineNo + ");");
//        System.out.println(atBuilder.toString());
        method.insertAt(lineNo, atBuilder.toString());
    }

    public void createInsertBefore(String payloadData, CtMethod method,
                                   String insertBefore) throws CannotCompileException {
        method.addLocalVariable("startTime", CtClass.longType);
        StringBuilder beforeBuilder = new StringBuilder();
        beforeBuilder.append("startTime = System.nanoTime();");
        if((insertBefore!=null) && !insertBefore.isEmpty()){
            beforeBuilder.append(insertBefore);
        }

        beforeBuilder.append("org.wso2.das.javaagent.instrumentation.AgentPublisher.publishEvents(" +
                "System.currentTimeMillis(),\"" + payloadData + ":start: \");");
//        System.out.println(beforeBuilder.toString());
        method.insertBefore(beforeBuilder.toString());
    }

    public void createInsertAfter(String payloadData, CtMethod method,
                                  String insertAfter) throws CannotCompileException {
        StringBuilder afterBuilder = new StringBuilder();
        if(insertAfter!=null && !insertAfter.isEmpty()) {
            afterBuilder.append(insertAfter);
        }
        afterBuilder.append("org.wso2.das.javaagent.instrumentation.AgentPublisher.publishEvents(" +
                "System.currentTimeMillis(),\"" + payloadData + ":end:\"+" +
                "String.valueOf(System.nanoTime()-startTime));");
//        System.out.println(afterBuilder.toString());
        method.insertAfter(afterBuilder.toString());
    }

    private String createPayloadData (String scenarioName, String className, String methodName){
        return scenarioName+":"+className+":"+methodName;
    }
}
