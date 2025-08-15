package com.suhasan.finance.transaction_service.performance;

import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@DisplayName("JMeter Load Tests")
public class JMeterLoadTest {

    private static final String JMETER_HOME = System.getProperty("jmeter.home", "target/jmeter");
    private static final String TEST_RESULTS_DIR = "target/jmeter-results";

    @BeforeAll
    static void setupJMeter() throws IOException {
        // Create JMeter home directory structure
        File jmeterHome = new File(JMETER_HOME);
        jmeterHome.mkdirs();
        
        File binDir = new File(jmeterHome, "bin");
        binDir.mkdirs();
        
        File libDir = new File(jmeterHome, "lib");
        libDir.mkdirs();
        
        // Create basic jmeter.properties file
        File propsFile = new File(binDir, "jmeter.properties");
        if (!propsFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(propsFile)) {
                fos.write("jmeter.save.saveservice.output_format=xml\n".getBytes());
                fos.write("jmeter.save.saveservice.response_data=false\n".getBytes());
                fos.write("jmeter.save.saveservice.samplerData=false\n".getBytes());
                fos.write("jmeter.save.saveservice.requestHeaders=false\n".getBytes());
                fos.write("jmeter.save.saveservice.responseHeaders=false\n".getBytes());
            }
        }

        // Initialize JMeter
        JMeterUtils.setJMeterHome(JMETER_HOME);
        JMeterUtils.loadJMeterProperties(propsFile.getAbsolutePath());
        JMeterUtils.initLocale();

        // Create results directory
        new File(TEST_RESULTS_DIR).mkdirs();
    }

    @Test
    @DisplayName("JMeter Load Test - Transaction Endpoints")
    void runTransactionLoadTest() throws Exception {
        // Create Test Plan
        TestPlan testPlan = new TestPlan("Transaction Service Load Test");
        testPlan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        testPlan.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.control.gui.TestPlanGui");
        testPlan.setUserDefinedVariables(null);

        // Create Thread Group
        ThreadGroup threadGroup = new ThreadGroup();
        threadGroup.setName("Transaction Users");
        threadGroup.setNumThreads(50); // 50 concurrent users
        threadGroup.setRampUp(30); // Ramp up over 30 seconds
        threadGroup.setProperty(TestElement.TEST_CLASS, ThreadGroup.class.getName());
        threadGroup.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.threads.gui.ThreadGroupGui");

        // Create Loop Controller
        LoopController loopController = new LoopController();
        loopController.setLoops(10); // Each user performs 10 transactions
        loopController.setFirst(true);
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        loopController.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.control.gui.LoopControlGui");
        loopController.initialize();
        threadGroup.setSamplerController(loopController);

        // Create HTTP Sampler for Transfer
        HTTPSamplerProxy transferSampler = createTransferSampler();
        
        // Create HTTP Sampler for Deposit
        HTTPSamplerProxy depositSampler = createDepositSampler();
        
        // Create HTTP Sampler for Withdrawal
        HTTPSamplerProxy withdrawalSampler = createWithdrawalSampler();

        // Create Header Manager
        HeaderManager headerManager = new HeaderManager();
        headerManager.add(new org.apache.jmeter.protocol.http.control.Header("Content-Type", "application/json"));
        headerManager.add(new org.apache.jmeter.protocol.http.control.Header("Authorization", "Bearer test-jwt-token"));
        headerManager.setProperty(TestElement.TEST_CLASS, HeaderManager.class.getName());
        headerManager.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.gui.HeaderPanel");

        // Create Summariser
        Summariser summer = new Summariser("summary");
        
        // Create Result Collector
        ResultCollector logger = new ResultCollector(summer);
        logger.setFilename(TEST_RESULTS_DIR + "/transaction-load-test-results.jtl");
        logger.setProperty(TestElement.TEST_CLASS, ResultCollector.class.getName());
        logger.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.visualizers.SummaryReportGui");

        // Build Test Plan Tree
        HashTree testPlanTree = new HashTree();
        HashTree threadGroupHashTree = testPlanTree.add(testPlan, threadGroup);
        threadGroupHashTree.add(headerManager);
        threadGroupHashTree.add(transferSampler);
        threadGroupHashTree.add(depositSampler);
        threadGroupHashTree.add(withdrawalSampler);
        testPlanTree.add(testPlan, logger);

        // Save Test Plan
        SaveService.saveTree(testPlanTree, new FileOutputStream(TEST_RESULTS_DIR + "/transaction-load-test.jmx"));

        // Run Test Plan
        StandardJMeterEngine jmeter = new StandardJMeterEngine();
        jmeter.configure(testPlanTree);
        jmeter.run();

        System.out.println("JMeter load test completed. Results saved to: " + TEST_RESULTS_DIR);
        System.out.println("Check the JTL file for detailed results and the JMX file for test plan configuration.");
    }

    private HTTPSamplerProxy createTransferSampler() {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setDomain("localhost");
        sampler.setPort(8080);
        sampler.setPath("/api/transactions/transfer");
        sampler.setMethod("POST");
        sampler.setName("Transfer Transaction");
        sampler.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        sampler.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui");
        
        // JSON body for transfer request
        String transferJson = """
            {
                "fromAccountId": "account-${__Random(1,1000)}",
                "toAccountId": "account-${__Random(1001,2000)}",
                "amount": ${__Random(10,1000)}.00,
                "currency": "USD",
                "description": "JMeter load test transfer"
            }
            """;
        sampler.addNonEncodedArgument("", transferJson, "");
        sampler.setPostBodyRaw(true);
        
        return sampler;
    }

    private HTTPSamplerProxy createDepositSampler() {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setDomain("localhost");
        sampler.setPort(8080);
        sampler.setPath("/api/transactions/deposit");
        sampler.setMethod("POST");
        sampler.setName("Deposit Transaction");
        sampler.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        sampler.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui");
        
        // JSON body for deposit request
        String depositJson = """
            {
                "accountId": "account-${__Random(1,1000)}",
                "amount": ${__Random(50,500)}.00,
                "currency": "USD",
                "description": "JMeter load test deposit"
            }
            """;
        sampler.addNonEncodedArgument("", depositJson, "");
        sampler.setPostBodyRaw(true);
        
        return sampler;
    }

    private HTTPSamplerProxy createWithdrawalSampler() {
        HTTPSamplerProxy sampler = new HTTPSamplerProxy();
        sampler.setDomain("localhost");
        sampler.setPort(8080);
        sampler.setPath("/api/transactions/withdraw");
        sampler.setMethod("POST");
        sampler.setName("Withdrawal Transaction");
        sampler.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        sampler.setProperty(TestElement.GUI_CLASS, "org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui");
        
        // JSON body for withdrawal request
        String withdrawalJson = """
            {
                "accountId": "account-${__Random(1,1000)}",
                "amount": ${__Random(10,200)}.00,
                "currency": "USD",
                "description": "JMeter load test withdrawal"
            }
            """;
        sampler.addNonEncodedArgument("", withdrawalJson, "");
        sampler.setPostBodyRaw(true);
        
        return sampler;
    }
}