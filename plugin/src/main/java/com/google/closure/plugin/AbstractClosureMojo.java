package com.google.closure.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.comoyo.maven.plugins.protoc.ProtocBundledMojo;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.closure.plugin.common.Cheats;
import com.google.closure.plugin.common.StableCssSubstitutionMapProvider;
import com.google.closure.plugin.common.DefaultProcessRunner;
import com.google.closure.plugin.common.GenfilesDirs;
import com.google.closure.plugin.common.SrcfilesDirs;
import com.google.closure.plugin.common.ToolFinder;
import com.google.closure.plugin.plan.Hash;
import com.google.closure.plugin.plan.PlanContext;
import com.google.closure.plugin.plan.PlanGraph;
import com.google.closure.plugin.proto.ProtoFinalOptions;
import com.google.closure.plugin.soy.SoyOptions;
import com.google.common.io.Files;
import com.google.common.io.Resources;

abstract class AbstractClosureMojo extends AbstractMojo {
  @Component
  private BuildContext buildContext;

  @Parameter(
      defaultValue="${project.basedir}",
      required=true,
      readonly=true)
  protected File baseDir;

  @Parameter(
      defaultValue="${project.build.directory}",
      required=true,
      readonly=true)
  protected File outputDir;

  @Parameter(
      defaultValue="${project.build.outputDirectory}",
      required=true,
      readonly=true)
  protected File outputClassesDir;

  /**
   * Directory root for compiled CSS and JS and other resources that are useful
   * on the client including the CSS rename map and CSS and JS source maps.
   * By default, this is under the classes directory so these files will be
   * packaged in the project JAR making these available as resources on the
   * class-path under "{@code /closure/}&hellip;".
   */
  @Parameter(
      property="closureOutputDirectory",
      // By default, the compiled JS, CSS, etc. are put in the classes directory
      // so that they are available as resources.
      defaultValue="${project.build.outputDirectory}/closure",
      required=true)
  protected File closureOutputDirectory;

  @Parameter(defaultValue="${project}", readonly=true, required=true)
  protected MavenProject project;


  @Parameter(
      defaultValue="${project.basedir}/src/main/css",
      readonly=true, required=true)
  protected File defaultCssSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/js",
      readonly=true, required=true)
  protected File defaultJsSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/js",
      readonly=true, required=true)
  protected File defaultJsTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/extern/js",
      readonly=true, required=true)
  protected File defaultJsExterns;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/js",
      readonly=true, required=true)
  protected File jsGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/js",
      readonly=true, required=true)
  protected File jsTestGenfiles;

  @Parameter(
      defaultValue="${project.basedir}/src/main/proto",
      readonly=true, required=true)
  protected File defaultProtoSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/proto",
      readonly=true, required=true)
  protected File defaultProtoTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/soy",
      readonly=true, required=true)
  protected File defaultSoySource;

  /**
   * Options for the closure template compiler.
   */
  @Parameter
  public SoyOptions soy;

  // TODO: look for something under ${project.compileSourceRoots} that is
  // also under project.build.directory.
  /** The source root for generated {@code .java} files. */
  @Parameter(
      defaultValue="${project.build.directory}/src/main/java",
      property="javaGenfiles",
      required=true)
  protected File javaGenfiles;

  /** The source root for generated {@code .java} test files. */
  @Parameter(
      defaultValue="${project.build.directory}/src/test/java",
      property="javaTestGenfiles",
      required=true)
  protected File javaTestGenfiles;

  @Parameter(
      defaultValue="{reldir}/{basename}{-orient}.css",
      readonly=true,
      required=true)
  protected String defaultCssOutputPathTemplate;

  @Parameter(
      defaultValue="{reldir}/source-map{-basename}{-orient}.json",
      readonly=true,
      required=true)
  protected String defaultCssSourceMapPathTemplate;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/proto/descriptors.pd",
      readonly=true,
      required=true)
  protected File defaultMainDescriptorFile;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/proto/descriptors.pd",
      readonly=true,
      required=true)
  protected File defaultTestDescriptorFile;

  /** The package name for generated Java classes. */
  @Parameter(
      defaultValue="${project.groupId}",
      required=true)
  protected String genJavaPackageName;


  @Override
  public void execute() throws MojoExecutionException {
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
    Log log = this.getLog();

    File cssRenameMapFile = new File(
        new File(closureOutputDirectory, "css"), "css-rename-map.json");
    log.info("Reading CSS rename map " + cssRenameMapFile);
    StableCssSubstitutionMapProvider substitutionMapProvider;
    try {
      substitutionMapProvider = new StableCssSubstitutionMapProvider(
          cssRenameMapFile);
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to read CSS rename map " + cssRenameMapFile, ex);
    }

    SrcfilesDirs srcfilesDirs;
    try {
      srcfilesDirs = new SrcfilesDirs(project);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to find src directories", ex);
    }

    GenfilesDirs genfilesDirs = new GenfilesDirs(
        outputDir,
        javaGenfiles, javaTestGenfiles,
        jsGenfiles, jsTestGenfiles);

    ImmutableList<Artifact> allArtifacts = ImmutableList.<Artifact>builder()
        .addAll(project.getDependencyArtifacts())
        .add(pluginDescriptor.getPluginArtifact())
        .build();

    Hash projectHash;
    {
      try (StringWriter sw = new StringWriter()) {
        new DefaultModelWriter().write(
            sw, ImmutableMap.<String, Object>of(), project.getModel());
        projectHash = Hash.hashString(sw.toString());
      } catch (IOException ex) {
        log.warn(ex);
        projectHash = null;
      }
    }

    PlanContext context = new PlanContext(
        DefaultProcessRunner.INSTANCE, pluginDescriptor, buildContext, log,
        srcfilesDirs, genfilesDirs, allArtifacts,
        outputDir, outputClassesDir, closureOutputDirectory,
        substitutionMapProvider);

    File planGraphFile = new File(
        context.outputDir, ".closure-plan-graph.ser");
    PlanGraph planGraph = new PlanGraph(context);
    boolean buildPlanGraph = true;
    if (projectHash != null) {
      try (InputStream pgIn =
           Files.asByteSource(planGraphFile).openBufferedStream()) {
        try (ObjectInputStream pgObjIn = new ObjectInputStream(pgIn)) {
          Hash storedHash = (Hash) pgObjIn.readObject();
          if (projectHash.equals(storedHash)) {
            planGraph.readFrom(pgObjIn);
            buildPlanGraph = false;
          }
        } catch (ClassNotFoundException ex) {
          throw new MojoExecutionException("Failed to load plan graph", ex);
        }
      } catch (@SuppressWarnings("unused") FileNotFoundException ex) {
        // Ok.
      } catch (IOException ex) {
        throw new MojoExecutionException("Failed to load plan graph", ex);
      }
    }

    if (buildPlanGraph) {
      formulatePlan(planGraph);
    }

    try {
      planGraph.execute();
    } catch (IOException ex) {
      throw new MojoExecutionException("Closure plan execution failed", ex);
    }

    log.debug("Writing rename map to " + cssRenameMapFile);
    try {
      cssRenameMapFile.getParentFile().mkdirs();
      try (Writer out = Files.asCharSink(cssRenameMapFile, Charsets.UTF_8)
              .openBufferedStream()) {
        OutputRenamingMapFormat.JSON.writeRenamingMap(
            substitutionMapProvider.get().getMappings(), out);
      }
    } catch (IOException ex) {
      log.warn("Problem writing CSS rename map", ex);
    }


    try (OutputStream pgOut =
             Files.asByteSink(planGraphFile).openBufferedStream()) {
      try (ObjectOutputStream pgObjOut = new ObjectOutputStream(pgOut)) {
        pgObjOut.writeObject(projectHash);
        planGraph.writeTo(pgObjOut);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to store plan graph", ex);
    }
  }

  protected abstract void formulatePlan(PlanGraph planGraph)
  throws MojoExecutionException;



  // For protoc support.
  @Parameter(defaultValue="${plugin}", required=true, readonly=true)
  protected PluginDescriptor pluginDescriptor;

  @Component
  private RepositorySystem repositorySystem;

//@Parameter(defaultValue="${project.remoteProjectRepositories}", readonly=true,
//           required=true)
  @Parameter(defaultValue="${project.remoteArtifactRepositories}",
             required=true, readonly=true )
  protected List<ArtifactRepository> remoteRepositories;

  ToolFinder<ProtoFinalOptions> protocExecutable() {
    return new ToolFinder<ProtoFinalOptions>() {

      static final String PROTOC_PLUGIN_GROUP_ID = "com.comoyo.maven.plugins";
      static final String PROTOC_PLUGIN_ARTIFACT_ID = "protoc-bundled-plugin";

      @Override
      public void find(
          Log log, ProtoFinalOptions options, ToolFinder.Sink out) {
        find(log, options.protobufVersion, options.protocExec, out);
      }

      @SuppressWarnings("synthetic-access")
      void find(
          Log log,
          Optional<String> protobufVersionOpt,
          Optional<File> protocExecOpt,
          Sink out) {
        ProtocBundledMojo protocBundledMojo = new ProtocBundledMojo();

        String protocPluginVersion = null;
        for (ComponentDependency d : pluginDescriptor.getDependencies()) {
          if (PROTOC_PLUGIN_GROUP_ID.equals(d.getGroupId())
              && PROTOC_PLUGIN_ARTIFACT_ID.equals(d.getArtifactId())) {
            protocPluginVersion = d.getVersion();
          }
        }
        Preconditions.checkNotNull(
            protocPluginVersion, "protoc-plugin version");

        PluginDescriptor protocPluginDescriptor = new PluginDescriptor();
        protocPluginDescriptor.setGroupId(PROTOC_PLUGIN_GROUP_ID);
        protocPluginDescriptor.setArtifactId(PROTOC_PLUGIN_ARTIFACT_ID);
        protocPluginDescriptor.setVersion(protocPluginVersion);

        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "pluginDescriptor", protocPluginDescriptor);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "project", project);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "repositorySystem", repositorySystem);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "remoteRepositories", remoteRepositories);
        if (protobufVersionOpt.isPresent()) {
          String protobufVersion = protobufVersionOpt.get();
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protobufVersion", protobufVersion);
        }
        if (protocExecOpt.isPresent()) {
          File protocExec = protocExecOpt.get();
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protocExec", protocExec);
        }

        try {
          Cheats.cheatCall(
              Void.class, ProtocBundledMojo.class,
              protocBundledMojo, "ensureProtocBinaryPresent"
              );
          File protocFile = Cheats.cheatGet(
              ProtocBundledMojo.class, protocBundledMojo,
              File.class, "protocExec");
          out.set(protocFile);
        } catch (InvocationTargetException ex) {
          Throwable targetException = ex.getTargetException();
          if (targetException instanceof MojoExecutionException
              && !protobufVersionOpt.isPresent()
              && !protocExecOpt.isPresent()) {
            // Fall back to the one blessed by the plugin POM.
            String pluginProtobufVersion = null;
            try {
              pluginProtobufVersion =
                  Resources.toString(
                      getClass().getResource("protobuf-version"),
                      Charsets.UTF_8)
                  .trim();
            } catch (IOException ioex) {
              log.warn(ioex);
            }
            if (pluginProtobufVersion != null) {
              // Falling back allows projects that have no need for .proto to
              // work without a protobuf-java <dependency>.
              log.info(
                  "Falling back to protobuf-version " + pluginProtobufVersion);
              find(
                  log,
                  Optional.of(pluginProtobufVersion),
                  protocExecOpt,
                  out);
            }
          } else {
            out.setProblem(targetException);
          }
        }
      }
    };
  }
}
