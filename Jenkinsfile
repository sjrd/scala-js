// If not a PR, this is a long-lived branch, which should have a nightly build
def triggers = []
if (!env.CHANGE_ID) {
  // This is the 1.x series: run nightly from Sunday to Friday
  triggers << cron('H H(0-2) * * 0-5')
}

// Setup properties of this job definition
properties([
  parameters([
    string(name: 'matrix', defaultValue: 'auto', description: 'The matrix to build (auto, quick, full)')
  ]),
  pipelineTriggers(triggers)
])

// Check whether the job was started by a timer
// See https://hopstorawpointers.blogspot.ch/2016/10/performing-nightly-build-steps-with.html
@NonCPS
def isJobStartedByTimer() {
  def startedByTimer = false
  def buildCauses = currentBuild.rawBuild.getCauses()
  for (buildCause in buildCauses) {
    if (buildCause != null) {
      def causeDescription = buildCause.getShortDescription()
      echo "shortDescription: ${causeDescription}"
      if (causeDescription.contains("Started by timer")) {
        startedByTimer = true
      }
    }
  }
  return startedByTimer
}
def startedByTimer = isJobStartedByTimer()

// Auto-select a matrix if it was not explicitly specified
def selectedMatrix = params.matrix
if (selectedMatrix == 'auto') {
  def reason = ''
  if (env.CHANGE_ID) {
    reason = "is a PR ${env.CHANGE_ID}"
    selectedMatrix = 'quick'
  } else {
    reason = "is not a PR, startedByTimer = $startedByTimer"
    if (startedByTimer) {
      selectedMatrix = 'full'
    } else {
      selectedMatrix = 'quick'
    }
  }
  echo("Auto-selected matrix: $selectedMatrix ($reason)")
} else {
  echo("Explicit matrix: $selectedMatrix")
}

def CIScriptPrelude = '''
LOCAL_HOME="/localhome/jenkins"
LOC_SBT_BASE="$LOCAL_HOME/scala-js-sbt-homes"
LOC_SBT_BOOT="$LOC_SBT_BASE/sbt-boot"
LOC_IVY_HOME="$LOC_SBT_BASE/sbt-home"
LOC_CS_CACHE="$LOC_SBT_BASE/coursier/cache"
TEST_LOCAL_IVY_HOME="$(pwd)/.ivy2-test-local"

rm -rf $TEST_LOCAL_IVY_HOME
mkdir $TEST_LOCAL_IVY_HOME
ln -s "$LOC_IVY_HOME/cache" "$TEST_LOCAL_IVY_HOME/cache"

export SBT_OPTS="-J-Xmx5G -J-XX:MaxPermSize=512M -Dsbt.boot.directory=$LOC_SBT_BOOT -Dsbt.ivy.home=$TEST_LOCAL_IVY_HOME -Divy.home=$TEST_LOCAL_IVY_HOME -Dsbt.global.base=$LOC_SBT_BASE"
export COURSIER_CACHE="$LOC_CS_CACHE"

export NODE_PATH="$HOME/node_modules/"

# Define setJavaVersion

setJavaVersion() {
  export JAVA_HOME=$HOME/apps/java-$1
  export PATH=$JAVA_HOME/bin:$PATH
}

# Define sbtretry

sbtretry() {
  local TIMEOUT=45m
  echo "RUNNING timeout -k 5 $TIMEOUT sbt" "$@"
  timeout -k 5 $TIMEOUT sbt $SBT_OPTS "$@"
  local CODE=$?
  if [ "$CODE" -eq 124 ]; then
    echo "TIMEOUT after" $TIMEOUT
  fi
  if [ "$CODE" -ne 0 ]; then
    echo "RETRYING timeout -k 5 $TIMEOUT sbt" "$@"
    timeout -k 5 $TIMEOUT sbt $SBT_OPTS "$@"
    CODE=$?
    if [ "$CODE" -eq 124 ]; then
      echo "TIMEOUT after" $TIMEOUT
    fi
    if [ "$CODE" -ne 0 ]; then
      echo "FAILED TWICE"
      return $CODE
    fi
  fi
}
'''

def Tasks = [
  "main": '''
    setJavaVersion $java
    npm install &&
    sbtretry ++$scala helloworld$v/run &&
    sbtretry 'set scalaJSStage in Global := FullOptStage' \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withPrettyPrint(true))' \
        ++$scala helloworld$v/run \
        helloworld$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withOptimizer(false))' \
        ++$scala helloworld$v/run \
        helloworld$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withSemantics(_.withAsInstanceOfs(CheckedBehavior.Unchecked)))' \
        ++$scala helloworld$v/run \
        helloworld$v/clean &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        helloworld$v/run \
        helloworld$v/clean &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        helloworld$v/run \
        helloworld$v/clean &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        helloworld$v/run &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        helloworld$v/run &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in helloworld.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        'set scalaJSStage in Global := FullOptStage' \
        helloworld$v/run \
        helloworld$v/clean &&
    sbtretry ++$scala testingExample$v/testHtmlJSDom &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in testingExample.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in testingExample.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        testingExample$v/testHtml \
        testingExample$v/clean &&
    sbtretry 'set scalaJSStage in Global := FullOptStage' \
        ++$scala testingExample$v/testHtmlJSDom \
        testingExample$v/clean &&
    sbtretry ++$scala testSuiteJVM$v/test testSuiteJVM$v/clean testSuiteExJVM$v/test testSuiteExJVM$v/clean &&
    sbtretry ++$scala testSuite$v/test &&
    sbtretry ++$scala testSuiteEx$v/test &&
    sbtretry 'set scalaJSStage in Global := FullOptStage' \
        ++$scala testSuiteEx$v/test &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in testSuiteEx.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in testSuiteEx.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        testSuiteEx$v/test &&
    sbtretry ++$scala testSuite$v/test:doc library$v/test compiler$v/test &&
    sbtretry ++$scala \
        'set scalaJSLinkerConfig in reversi.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in reversi.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        reversi$v/fastLinkJS \
        reversi$v/fullLinkJS \
        reversi$v/clean &&
    sbtretry ++$scala \
        reversi$v/fastLinkJS \
        reversi$v/fullLinkJS \
        reversi$v/checksizes &&
    sbtretry ++$scala compiler$v/compile:doc library$v/compile:doc \
        testInterface$v/compile:doc testBridge$v/compile:doc &&
    sbtretry ++$scala headerCheck &&
    sbtretry ++$scala partest$v/fetchScalaSource &&
    sbtretry ++$scala \
        library$v/mimaReportBinaryIssues \
        testInterface$v/mimaReportBinaryIssues \
        jUnitRuntime$v/mimaReportBinaryIssues
  ''',

  "test-suite-default-esversion": '''
    setJavaVersion $java
    npm install &&
    sbtretry ++$scala jUnitTestOutputsJVM$v/test jUnitTestOutputsJS$v/test testBridge$v/test \
        'set scalaJSStage in Global := FullOptStage' jUnitTestOutputsJS$v/test testBridge$v/test &&
    sbtretry ++$scala $testSuite$v/test $testSuite$v/testHtmlJSDom &&
    sbtretry 'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/testHtmlJSDom \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withAllowBigIntsForLongs(true)))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withAllowBigIntsForLongs(true)).withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withAvoidLetsAndConsts(false).withAvoidClasses(false)))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withAvoidLetsAndConsts(false).withAvoidClasses(false)))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalacOptions in $testSuite.v$v += "-Xexperimental"' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalacOptions in $testSuite.v$v += "-Xexperimental"' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test
  ''',

  "test-suite-custom-esversion-force-polyfills": '''
    setJavaVersion $java
    npm install &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set Seq(jsEnv in $testSuite.v$v := new NodeJSEnvForcePolyfills(ESVersion.$esVersion), MyScalaJSPlugin.wantSourceMaps in $testSuite.v$v := ("$esVersion" != "ES5_1"))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean
  ''',

  "test-suite-custom-esversion": '''
    setJavaVersion $java
    npm install &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= makeCompliant' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= { _.withSemantics(_.withStrictFloats(false)) }' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion).withAllowBigIntsForLongs(true)))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion).withAllowBigIntsForLongs(true)).withOptimizer(false))' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.CommonJSModule))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test \
        $testSuite$v/clean &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleSplitStyle(ModuleSplitStyle.SmallestModules))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        ++$scala $testSuite$v/test &&
    sbtretry 'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withESFeatures(_.withESVersion(ESVersion.$esVersion)))' \
        'set scalaJSLinkerConfig in $testSuite.v$v ~= (_.withModuleKind(ModuleKind.ESModule))' \
        'set scalaJSStage in Global := FullOptStage' \
        ++$scala $testSuite$v/test
  ''',

  /* For the bootstrap tests to be able to call
   * `testSuite/test:fastOptJS`, `scalaJSStage in testSuite` must be
   * `FastOptStage`, even when `scalaJSStage in Global` is `FullOptStage`.
   */
  "bootstrap": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala linker$v/test &&
    sbt linkerPrivateLibrary/test &&
    sbt ++$scala irJS$v/test linkerJS$v/test linkerInterfaceJS$v/test &&
    sbt 'set scalaJSStage in Global := FullOptStage' \
        'set scalaJSStage in testSuite.v$v := FastOptStage' \
        ++$scala irJS$v/test linkerJS$v/test linkerInterfaceJS$v/test &&
    sbt ++$scala testSuite$v/bootstrap:test &&
    sbt 'set scalaJSStage in Global := FullOptStage' \
        'set scalaJSStage in testSuite.v$v := FastOptStage' \
        ++$scala testSuite$v/bootstrap:test &&
    sbt ++$scala irJS$v/mimaReportBinaryIssues \
        linkerInterfaceJS$v/mimaReportBinaryIssues linkerJS$v/mimaReportBinaryIssues
  ''',

  "tools": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala ir$v/test linkerInterface$v/test \
        linker$v/compile testAdapter$v/test \
        ir$v/mimaReportBinaryIssues \
        linkerInterface$v/mimaReportBinaryIssues linker$v/mimaReportBinaryIssues \
        testAdapter$v/mimaReportBinaryIssues &&
    sbt ++$scala ir$v/compile:doc \
        linkerInterface$v/compile:doc linker$v/compile:doc \
        testAdapter$v/compile:doc
  ''',

  "tools-sbtplugin": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala ir$v/test linkerInterface$v/compile \
        linker$v/compile testAdapter$v/test \
        sbtPlugin/package \
        ir$v/mimaReportBinaryIssues \
        linkerInterface$v/mimaReportBinaryIssues linker$v/mimaReportBinaryIssues \
        testAdapter$v/mimaReportBinaryIssues \
        sbtPlugin/mimaReportBinaryIssues &&
    sbt ++$scala scalastyleCheck &&
    sbt ++$scala ir$v/compile:doc \
        linkerInterface$v/compile:doc linker$v/compile:doc \
        testAdapter$v/compile:doc \
        sbtPlugin/compile:doc &&
    sbt sbtPlugin/scripted
  ''',

  "partest-noopt": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala package "partestSuite$v/testOnly -- --showDiff"
  ''',

  "partest-fastopt": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala package "partestSuite$v/testOnly -- --fastOpt --showDiff"
  ''',

  "partest-fullopt": '''
    setJavaVersion $java
    npm install &&
    sbt ++$scala package "partestSuite$v/testOnly -- --fullOpt --showDiff"
  '''
]

def mainJavaVersion = "1.8"
def otherJavaVersions = ["11", "16"]
def allJavaVersions = otherJavaVersions.clone()
allJavaVersions << mainJavaVersion

def mainScalaVersion = "2.12.15"
def mainScalaVersions = ["2.11.12", "2.12.15", "2.13.6"]
def otherScalaVersions = [
  "2.11.12",
  "2.12.1",
  "2.12.2",
  "2.12.3",
  "2.12.4",
  "2.12.5",
  "2.12.6",
  "2.12.7",
  "2.12.8",
  "2.12.9",
  "2.12.10",
  "2.12.11",
  "2.12.12",
  "2.12.13",
  "2.12.14",
  "2.13.0",
  "2.13.1",
  "2.13.2",
  "2.13.3",
  "2.13.4",
  "2.13.5"
]

def allESVersions = [
  "ES5_1",
  "ES2015",
  // "ES2016", // We do not use anything specifically from ES2016
  "ES2017",
  "ES2018",
  // "ES2019", // We do not use anything specifically from ES2019
  "ES2020"
  // "ES2021", // We do not use anything specifically from ES2021
]

// Scala 2.11 does not support newer Java versions
def isExcludedForScala211(javaVersion) {
  return javaVersion != "1.8" && javaVersion != "11"
}

def isExcludedScalaJavaCombo(scalaVersion, javaVersion) {
  return scalaVersion.startsWith("2.11.") && isExcludedForScala211(javaVersion)
}

// The 'quick' matrix
def quickMatrix = []
mainScalaVersions.each { scalaVersion ->
  allJavaVersions.each { javaVersion ->
    if (!isExcludedScalaJavaCombo(scalaVersion, javaVersion))
      quickMatrix.add([task: "main", scala: scalaVersion, java: javaVersion])
  }
  quickMatrix.add([task: "test-suite-default-esversion", scala: scalaVersion, java: mainJavaVersion, testSuite: "testSuite"])
  quickMatrix.add([task: "test-suite-custom-esversion", scala: scalaVersion, java: mainJavaVersion, esVersion: "ES5_1", testSuite: "testSuite"])
  quickMatrix.add([task: "test-suite-default-esversion", scala: scalaVersion, java: mainJavaVersion, testSuite: "scalaTestSuite"])
  quickMatrix.add([task: "test-suite-custom-esversion", scala: scalaVersion, java: mainJavaVersion, esVersion: "ES5_1", testSuite: "scalaTestSuite"])
  quickMatrix.add([task: "bootstrap", scala: scalaVersion, java: mainJavaVersion])
  quickMatrix.add([task: "partest-fastopt", scala: scalaVersion, java: mainJavaVersion])
}
allESVersions.each { esVersion ->
  quickMatrix.add([task: "test-suite-custom-esversion-force-polyfills", scala: mainScalaVersion, java: mainJavaVersion, esVersion: esVersion, testSuite: "testSuite"])
}
allJavaVersions.each { javaVersion ->
  if (!isExcludedForScala211(javaVersion)) {
    // the sbt plugin tests want to compile everything for 2.11, 2.12 and 2.13
    quickMatrix.add([task: "tools-sbtplugin", scala: "2.12.15", java: javaVersion])
    quickMatrix.add([task: "tools", scala: "2.11.12", java: javaVersion])
  }
  quickMatrix.add([task: "tools", scala: "2.13.6", java: javaVersion])
}

// The 'full' matrix
def fullMatrix = quickMatrix.clone()
otherScalaVersions.each { scalaVersion ->
  fullMatrix.add([task: "main", scala: scalaVersion, java: mainJavaVersion])
}
mainScalaVersions.each { scalaVersion ->
  otherJavaVersions.each { javaVersion ->
    if (!isExcludedScalaJavaCombo(scalaVersion, javaVersion))
      quickMatrix.add([task: "test-suite-default-esversion", scala: scalaVersion, java: javaVersion, testSuite: "testSuite"])
  }
  fullMatrix.add([task: "partest-noopt", scala: scalaVersion, java: mainJavaVersion])
  fullMatrix.add([task: "partest-fullopt", scala: scalaVersion, java: mainJavaVersion])
}

def Matrices = [
  quick: quickMatrix,
  full: fullMatrix
]

if (!Matrices.containsKey(selectedMatrix)) {
  error("Nonexistent matrix '$selectedMatrix'")
}
def matrix = Matrices[selectedMatrix]

buildDefs = [:]
matrix.each { taskDef ->
  def taskName = taskDef.task
  if (!Tasks.containsKey(taskName)) {
    error("Nonexistent task '$taskName'")
  }
  def taskStr = Tasks[taskName]
  def fullTaskName = taskName

  taskDef.each { name, value ->
    if (name != 'task') {
      taskStr = taskStr.replace('$' + name, value)
      fullTaskName += " $name=$value"
    }
  }

  def suffix = taskDef.scala.split('\\.')[0..1].join('_')
  taskStr = taskStr.replace('$v', suffix)

  def ciScript = CIScriptPrelude + taskStr

  buildDefs.put(fullTaskName, {
    node('linuxworker') {
      checkout scm
      sh "git clean -fdx && rm -rf partest/fetchedSources/"
      writeFile file: 'ciscript.sh', text: ciScript, encoding: 'UTF-8'
      retry(2) {
        timeout(time: 4, unit: 'HOURS') {
          sh "echo '$fullTaskName' && cat ciscript.sh && sh ciscript.sh"
        }
      }
    }
  })
}

ansiColor('xterm') {
  stage('Test') {
    parallel(buildDefs)
  }
}
