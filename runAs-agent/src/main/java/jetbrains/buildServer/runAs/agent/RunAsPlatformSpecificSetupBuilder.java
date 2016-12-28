package jetbrains.buildServer.runAs.agent;

import java.io.File;
import java.util.*;
import jetbrains.buildServer.dotNet.buildRunner.agent.*;
import jetbrains.buildServer.runAs.common.Constants;
import org.jetbrains.annotations.NotNull;

public class RunAsPlatformSpecificSetupBuilder implements CommandLineSetupBuilder {
  static final String TOOL_FILE_NAME = "runAs";
  static final String ARGS_EXT = ".args";
  private final UserCredentialsService myUserCredentialsService;
  private final RunnerParametersService myRunnerParametersService;
  private final FileService myFileService;
  private final AccessControlListProvider myAccessControlListProvider;
  private final ResourcePublisher myBeforeBuildPublisher;
  private final AccessControlResource myAccessControlResource;
  private final ResourceGenerator<UserCredentials> myUserCredentialsGenerator;
  private final ResourceGenerator<RunAsParams> myRunAsCmdGenerator;
  private final CommandLineArgumentsService myCommandLineArgumentsService;
  private final FileAccessService myFileAccessService;
  private final RunAsLogger myRunAsLogger;
  private final String myCommandFileExtension;

  public RunAsPlatformSpecificSetupBuilder(
    @NotNull final UserCredentialsService userCredentialsService,
    @NotNull final RunnerParametersService runnerParametersService,
    @NotNull final FileService fileService,
    @NotNull final AccessControlListProvider accessControlListProvider,
    @NotNull final ResourcePublisher beforeBuildPublisher,
    @NotNull final AccessControlResource accessControlResource,
    @NotNull final ResourceGenerator<UserCredentials> userCredentialsGenerator,
    @NotNull final ResourceGenerator<RunAsParams> runAsCmdGenerator,
    @NotNull final CommandLineArgumentsService commandLineArgumentsService,
    @NotNull final FileAccessService fileAccessService,
    @NotNull final RunAsLogger runAsLogger,
    @NotNull final String commandFileExtension) {
    myUserCredentialsService = userCredentialsService;
    myRunnerParametersService = runnerParametersService;
    myFileService = fileService;
    myAccessControlListProvider = accessControlListProvider;
    myBeforeBuildPublisher = beforeBuildPublisher;
    myAccessControlResource = accessControlResource;
    myUserCredentialsGenerator = userCredentialsGenerator;
    myRunAsCmdGenerator = runAsCmdGenerator;
    myCommandLineArgumentsService = commandLineArgumentsService;
    myFileAccessService = fileAccessService;
    myRunAsLogger = runAsLogger;
    myCommandFileExtension = commandFileExtension;
  }

  @NotNull
  @Override
  public Iterable<CommandLineSetup> build(@NotNull final CommandLineSetup commandLineSetup) {
    // Get userCredentials
    final UserCredentials userCredentials = myUserCredentialsService.tryGetUserCredentials();
    if(userCredentials == null) {
      return Collections.singleton(commandLineSetup);
    }

    // Resources
    final ArrayList<CommandLineResource> resources = new ArrayList<CommandLineResource>();
    resources.addAll(commandLineSetup.getResources());

    // Settings
    final File settingsFile = myFileService.getTempFileName(ARGS_EXT);
    resources.add(new CommandLineFile(myBeforeBuildPublisher, settingsFile.getAbsoluteFile(), myUserCredentialsGenerator.create(userCredentials)));

    // Command
    List<CommandLineArgument> cmdLineArgs = new ArrayList<CommandLineArgument>();
    cmdLineArgs.add(new CommandLineArgument(commandLineSetup.getToolPath(), CommandLineArgument.Type.PARAMETER));
    cmdLineArgs.addAll(commandLineSetup.getArgs());

    final RunAsParams params = new RunAsParams(myCommandLineArgumentsService.createCommandLineString(cmdLineArgs));

    final File commandFile = myFileService.getTempFileName(myCommandFileExtension);
    resources.add(new CommandLineFile(myBeforeBuildPublisher, commandFile.getAbsoluteFile(), myRunAsCmdGenerator.create(params)));
    myAccessControlResource.addEntry(new AccessControlEntry(commandFile, AccessControlAccount.forUser(userCredentials.getUser()), EnumSet.of(AccessPermissions.AllowExecute)));
    final AccessControlList beforeBuildStepAcl = myAccessControlListProvider.getBeforeBuildStepAcl(userCredentials);
    for (AccessControlEntry ace: beforeBuildStepAcl) {
      myAccessControlResource.addEntry(ace);
    }

    resources.add(myAccessControlResource);

    final CommandLineSetup runAsCommandLineSetup = new CommandLineSetup(
      getTool().getAbsolutePath(),
      Arrays.asList(
        new CommandLineArgument(settingsFile.getAbsolutePath(), CommandLineArgument.Type.PARAMETER),
        new CommandLineArgument(commandFile.getAbsolutePath(), CommandLineArgument.Type.PARAMETER),
        new CommandLineArgument(userCredentials.getPassword(), CommandLineArgument.Type.PARAMETER)),
      resources);

    myRunAsLogger.LogRunAs(runAsCommandLineSetup);
    return Collections.singleton(runAsCommandLineSetup);
  }

  private File getTool() {
    final File path = new File(myRunnerParametersService.getToolPath(Constants.RUN_AS_TOOL_NAME), TOOL_FILE_NAME + myCommandFileExtension);
    myFileService.validatePath(path);
    final AccessControlList acl = new AccessControlList(Arrays.asList(new AccessControlEntry(path, AccessControlAccount.forCurrent(), EnumSet.of(AccessPermissions.AllowExecute))));
    myFileAccessService.setAccess(acl);
    return path;
  }
}