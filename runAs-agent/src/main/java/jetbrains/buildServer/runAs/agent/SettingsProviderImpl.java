package jetbrains.buildServer.runAs.agent;

import java.util.List;
import jetbrains.buildServer.dotNet.buildRunner.agent.CommandLineArgumentsService;
import jetbrains.buildServer.dotNet.buildRunner.agent.RunnerParametersService;
import jetbrains.buildServer.runAs.common.Constants;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SettingsProviderImpl implements SettingsProvider {
  private final RunnerParametersService myParametersService;
  private final BuildFeatureParametersService myBuildFeatureParametersService;
  private final CommandLineArgumentsService myCommandLineArgumentsService;

  public SettingsProviderImpl(
    @NotNull final RunnerParametersService parametersService,
    @NotNull final BuildFeatureParametersService buildFeatureParametersService,
    @NotNull final CommandLineArgumentsService commandLineArgumentsService) {
    myParametersService = parametersService;
    myBuildFeatureParametersService = buildFeatureParametersService;
    myCommandLineArgumentsService = commandLineArgumentsService;
  }

  @Nullable
  @Override
  public Settings tryGetSettings() {
    // Get parameters
    final String userName = tryGetParameter(Constants.USER_VAR);
    if(StringUtil.isEmptyOrSpaces(userName)) {
      return null;
    }

    final String password = tryGetParameter(Constants.PASSWORD_VAR);
    if(StringUtil.isEmptyOrSpaces(password)) {
      return null;
    }

    String additionalArgs = tryGetParameter(Constants.ADDITIONAL_ARGS_VAR);
    if(StringUtil.isEmptyOrSpaces(additionalArgs)) {
      additionalArgs = "";
    }

    return new Settings(userName, password, myCommandLineArgumentsService.parseCommandLineArguments(additionalArgs));
  }

  @Nullable
  private String tryGetParameter(@NotNull final String paramName)
  {
    final List<String> paramValues = myBuildFeatureParametersService.getBuildFeatureParameters(Constants.BUILD_FEATURE_TYPE, paramName);
    if (paramValues.size() != 0) {
      final String paramValue = paramValues.get(0);
      if(paramValue != null)
      {
        return paramValue;
      }
    }

    return myParametersService.tryGetConfigParameter(paramName);
  }

}