package org.sagebionetworks.bridge.stackbuilder;

import static org.sagebionetworks.bridge.stackbuilder.LogHelper.logInfo;
import static org.sagebionetworks.bridge.stackbuilder.LogHelper.logWarning;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.fluent.Request;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.config.PropertiesConfig;

public class BridgeStackBuilder {
    private static final String HOME_DIR = System.getProperty("user.home");
    private static final String FILE_PATH_SERVER_CONF = HOME_DIR + "/BridgeServer2.conf";
    private static final String FILE_PATH_WORKER_CONF = HOME_DIR + "/BridgeWorkerPlatform.conf";
    private static final int POLL_STACK_DELAY_SECONDS = 60;
    private static final int POLL_STACK_MAX_ATTEMPTS = 60;
    private static final String URL_SERVER_CONF =
            "https://raw.githubusercontent.com/Sage-Bionetworks/BridgeServer2/develop/src/main/resources/BridgeServer2.conf";
    private static final String URL_WORKER_CONF =
            "https://raw.githubusercontent.com/Sage-Bionetworks/BridgeWorkerPlatform/develop/src/main/resources/BridgeWorkerPlatform.conf";
    private static final String URL_DB_TEMPLATE =
            "https://raw.githubusercontent.com/Sage-Bionetworks/BridgeServer2-infra/develop/templates/bridgeserver2-db.yaml";
    private static final String URL_SERVER_TEMPLATE =
            "https://raw.githubusercontent.com/Sage-Bionetworks/BridgeServer2-infra/develop/templates/bridgeserver2.yaml";
    private static final String URL_WORKER_TEMPLATE =
            "https://raw.githubusercontent.com/Sage-Bionetworks/BridgeWorkerPlatform-infra/develop/templates/bridgeworker.yaml";

    private CloudFormationClient cloudFormationClient;
    private S3Client s3Client;
    private Config serverConfig;
    private Config workerConfig;
    private String stackNameDb;
    private String stackNameServer;
    private String stackNameSuffix;
    private String stackNameWorker;
    private String dbTemplateOverride;
    private String dbTemplateOverrideContent;
    private String serverTemplateOverride;
    private String serverTemplateOverrideContent;
    private String workerTemplateOverride;
    private String workerTemplateOverrideContent;

    public static void main(String[] args) throws ParseException {
        //todo
    }

    private void initialize(String[] args) throws IOException, ParseException {
        // Parse command line args with Apache Commons CLI.
        Options options = new Options();
        options.addOption("d", "db-template", true,
                "Database CloudFormation template file path");
        options.addOption("s", "server-template", true,
                "Server CloudFormation template file path");
        options.addOption("S", "server-config", true,
                "Server config file path");
        options.addOption("w", "worker-template", true,
                "Worker CloudFormation template file path");
        options.addOption("W", "worker-config", true,
                "Worker config file path");

        CommandLine cmd = new DefaultParser().parse(options, args);
        dbTemplateOverride = cmd.getOptionValue("d");
        serverTemplateOverride = cmd.getOptionValue("s");
        workerTemplateOverride = cmd.getOptionValue("w");
        String serverConfigOverride = cmd.getOptionValue("S");
        String workerConfigOverride = cmd.getOptionValue("W");

        // Load configs.
        serverConfig = loadConfig(serverConfigOverride, URL_SERVER_CONF, FILE_PATH_SERVER_CONF);
        workerConfig = loadConfig(workerConfigOverride, URL_WORKER_CONF, FILE_PATH_WORKER_CONF);

        // Set stack name suffix.
        stackNameSuffix = "cloud-" + serverConfig.get("bridge.user");

        // Init AWS services.
        cloudFormationClient = CloudFormationClient.builder().region(Region.US_EAST_1).build();
        s3Client = S3Client.builder().region(Region.US_EAST_1).build();
    }

    private void execute() {
        //todo
    }

    // Create (or update) the DB stack.
    private void createDbStack() throws IOException {
        stackNameDb = "bridgeserver2-db-" + stackNameSuffix;

        // Download template.
        if (dbTemplateOverride != null) {
            dbTemplateOverrideContent = readFile(dbTemplateOverride);
        }

        // Make stack params.
        List<Parameter> paramList = new ArrayList<>();
        paramList.add(Parameter.builder().parameterKey("HibernateConnectionPassword")
                .parameterValue(serverConfig.get("hibernate.connection.password")).build());
        paramList.add(Parameter.builder().parameterKey("HibernateConnectionUsername")
                .parameterValue(serverConfig.get("hibernate.connection.username")).build());

        // Create/Update the stack.
        createOrUpdateStack(stackNameDb, URL_DB_TEMPLATE, dbTemplateOverrideContent, paramList);
    }

    // Create (or update) the server stack.
    private void createServerStack() throws IOException {
        stackNameServer = "bridgeserver2-" + stackNameSuffix;

        // Download template.
        if (serverTemplateOverride != null) {
            serverTemplateOverrideContent = readFile(serverTemplateOverride);
        }

        // We need to create a few S3 buckets.
        //String attachmentBucket = "org-sagebridge-attachment-" + stackNameSuffix;
        //CreateBucketRequest createBucketRequest = CreateBucketRequest.builder().bucket(attachmentBucket).build();
        //s3Client.createBucket(createBucketRequest);

        // Make stack params.
        List<Parameter> paramList = new ArrayList<>();
        //paramList.add(Parameter.builder().parameterKey("AppDeployBucket")
        //        .parameterValue("org-sagebridge-bridgeserver2-deployment-" + stackNameSuffix).build());
        //paramList.add(Parameter.builder().parameterKey("AttachmentBucket")
        //        .parameterValue(attachmentBucket).build());
        paramList.add(Parameter.builder().parameterKey("BootstrapAdminEmail")
                .parameterValue(serverConfig.get("admin.email")).build());
        paramList.add(Parameter.builder().parameterKey("BootstrapAdminPassword")
                .parameterValue(serverConfig.get("admin.password")).build());
        paramList.add(Parameter.builder().parameterKey("BridgeDBStackName")
                .parameterValue(stackNameDb).build());
        paramList.add(Parameter.builder().parameterKey("BridgeHealthcodeRedisKey")
                .parameterValue(serverConfig.get("bridge.healthcode.redis.key")).build());
        paramList.add(Parameter.builder().parameterKey("BridgeUser")
                .parameterValue("cloud-" + serverConfig.get("bridge.user")).build());
        paramList.add(Parameter.builder().parameterKey("BucketSuffix")
                .parameterValue(stackNameSuffix).build());
        paramList.add(Parameter.builder().parameterKey("DNSHostname")
                .parameterValue("bridgeserver2-" + stackNameSuffix).build());
        paramList.add(Parameter.builder().parameterKey("ExporterSynapseAccessToken")
                .parameterValue(serverConfig.get("exporter.synapse.access.token")).build());
        paramList.add(Parameter.builder().parameterKey("ExporterSynapseUser")
                .parameterValue(serverConfig.get("exporter.synapse.user")).build());
        paramList.add(Parameter.builder().parameterKey("HibernateConnectionPassword")
                .parameterValue(serverConfig.get("hibernate.connection.password")).build());
        paramList.add(Parameter.builder().parameterKey("HibernateConnectionUsername")
                .parameterValue(serverConfig.get("hibernate.connection.username")).build());
        paramList.add(Parameter.builder().parameterKey("HibernateConnectionUsessl")
                .parameterValue(serverConfig.get("hibernate.connection.useSSL")).build());
        paramList.add(Parameter.builder().parameterKey("HostPostfix")
                .parameterValue("-" + stackNameSuffix + ".sagebridge.org").build());
        paramList.add(Parameter.builder().parameterKey("SynapseAccessToken")
                .parameterValue(serverConfig.get("synapse.access.token")).build());
        paramList.add(Parameter.builder().parameterKey("SynapseUser")
                .parameterValue(serverConfig.get("synapse.user")).build());
        paramList.add(Parameter.builder().parameterKey("SynapseOAuthClientId")
                .parameterValue(serverConfig.get("synapse.oauth.client.id")).build());
        paramList.add(Parameter.builder().parameterKey("SynapseOAuthClientSecret")
                .parameterValue(serverConfig.get("synapse.oauth.client.secret")).build());

        // Create/Update the stack.
        createOrUpdateStack(stackNameServer, serverTemplateOverrideContent, URL_SERVER_TEMPLATE, paramList);
    }

    // Create (or update) the Worker stack.
    private void createWorkerStack() throws IOException {
        stackNameWorker = "bridgeserver2-worker-" + stackNameSuffix;

        // Download template.
        if (workerTemplateOverride != null) {
            workerTemplateOverrideContent = readFile(workerTemplateOverride);
        }

        // Make stack params.
        List<Parameter> paramList = new ArrayList<>();
        paramList.add(Parameter.builder().parameterKey("BridgeUser")
                .parameterValue("cloud-" + workerConfig.get("bridge.user")).build());
        paramList.add(Parameter.builder().parameterKey("BridgeWorkerEmail")
                .parameterValue(workerConfig.get("bridge.worker.email")).build());
        paramList.add(Parameter.builder().parameterKey("BridgeWorkerPassword")
                .parameterValue(workerConfig.get("bridge.worker.password")).build());
        paramList.add(Parameter.builder().parameterKey("ParticipantRosterBucket")
                .parameterValue("org-sagebridge-participantroster-" + stackNameSuffix).build());
        paramList.add(Parameter.builder().parameterKey("RawHealthDataBucket")
                .parameterValue("org-sagebridge-rawhealthdata-" + stackNameSuffix).build());
        paramList.add(Parameter.builder().parameterKey("SynapseAccessToken")
                .parameterValue(workerConfig.get("synapse.access.token")).build());
        paramList.add(Parameter.builder().parameterKey("SynapsePrincipalId")
                .parameterValue(workerConfig.get("synapse.principal.id")).build());
        paramList.add(Parameter.builder().parameterKey("SynapseUser")
                .parameterValue(workerConfig.get("synapse.user")).build());
        paramList.add(Parameter.builder().parameterKey("UploadBucket")
                .parameterValue("org-sagebridge-upload-" + stackNameSuffix).build());
        paramList.add(Parameter.builder().parameterKey("WorkerSqsQueueUrl")
                .parameterValue(workerConfig.get("workerPlatform.request.sqs.queue.url")).build());

        // Create/Update the stack.
        createOrUpdateStack(stackNameWorker, URL_WORKER_TEMPLATE, workerTemplateOverrideContent, paramList);
    }

    private void createOrUpdateStack(String stackName, String templateOverrideContent, String templateUrl,
            List<Parameter> paramList) {
        // Does the stack already exist?
        DescribeStacksResponse describeStacksResponse = describeStack(stackName);
        if (describeStacksResponse.stacks().size() > 0) {
            // Update stack instead.
            UpdateStackRequest.Builder requestBuilder = UpdateStackRequest.builder().stackName(stackName);
            if (templateOverrideContent != null) {
                requestBuilder.templateBody(templateOverrideContent);
            } else {
                requestBuilder.templateURL(templateUrl);
            }
            requestBuilder.parameters(paramList);
            cloudFormationClient.updateStack(requestBuilder.build());
        } else {
            // Create stack.
            CreateStackRequest.Builder requestBuilder = CreateStackRequest.builder().stackName(stackName);
            if (templateOverrideContent != null) {
                requestBuilder.templateBody(templateOverrideContent);
            } else {
                requestBuilder.templateURL(templateUrl);
            }
            requestBuilder.parameters(paramList);
            cloudFormationClient.createStack(requestBuilder.build());
        }

        // Poll for stack completion.
        pollStackCompletion(stackName);
    }

    // Helper method to poll stack creation or update until it completes.
    private void pollStackCompletion(String stackName) {
        for (int tries = 1; tries <= POLL_STACK_MAX_ATTEMPTS; tries++) {
            // Wait for a bit.
            try {
                TimeUnit.SECONDS.sleep(POLL_STACK_DELAY_SECONDS);
            } catch (InterruptedException ex) {
                logWarning("Interrupted while polling for stack completion for stack " + stackName);
                Thread.currentThread().interrupt();
            }

            // Describe stack.
            logInfo("Polling for stack completion for stack " + stackName + ", attempt " + tries);
            DescribeStacksResponse describeStacksResponse = describeStack(stackName);

            // There should only be one stack.
            List<Stack> stackList = describeStacksResponse.stacks();
            if (stackList.size() > 1) {
                logWarning("Expected 1 stack, but got " + stackList.size());
            } else if (stackList.isEmpty()) {
                throw new IllegalStateException("Stack " + stackName + " not found");
            }

            Stack stack = stackList.get(0);
            if (!stack.stackStatusAsString().contains("IN_PROGRESS")) {
                // Stack is ready. Return;
                return;
            }

            // Stack is not ready yet. Loop around and try again.
        }
    }

    private DescribeStacksResponse describeStack(String stackName) {
        DescribeStacksRequest describeStacksRequest = DescribeStacksRequest.builder().stackName(stackName).build();
        return cloudFormationClient.describeStacks(describeStacksRequest);
    }

    // Helper method to load configs. This downloads the base config from the given URL, then overrides it with the
    // local user config, if it exists.
    private static Config loadConfig(String baseConfigOverride, String baseConfigUrl, String localUserConfigPath)
            throws IOException {
        Path defaultConfigPath;

        if (baseConfigOverride != null) {
            defaultConfigPath = Paths.get(baseConfigOverride);
        } else {
            // Create temp file to download to.
            File tempFile = File.createTempFile("bridge-config", ".conf");
            try (FileOutputStream tempFileOutputStream = new FileOutputStream(tempFile)) {
                // Download file.
                Request.Get(baseConfigUrl).execute().returnResponse().getEntity().writeTo(tempFileOutputStream);
            }
            defaultConfigPath = Paths.get(tempFile.getAbsolutePath());
        }

        // Load configs.
        Path localConfigPath = Paths.get(localUserConfigPath);

        if (Files.exists(localConfigPath)) {
            return new PropertiesConfig(defaultConfigPath, localConfigPath);
        } else {
            return new PropertiesConfig(defaultConfigPath);
        }
    }

    // Helper method to read a file into a string.
    private static String readFile(String path) throws IOException {
        return com.google.common.io.Files.asCharSource(new File(path), Charsets.UTF_8).read();
    }
}
