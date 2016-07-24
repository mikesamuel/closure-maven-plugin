package com.google.common.html.plugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.repository.ComponentDependency;

import com.comoyo.maven.plugins.protoc.ProtocBundledMojo;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.css.CssOptions;
import com.google.common.html.plugin.css.CssPlanner;
import com.google.common.html.plugin.extract.Extract;
import com.google.common.html.plugin.extract.ExtractPlanner;
import com.google.common.html.plugin.plan.HashStore;
import com.google.common.html.plugin.plan.Plan;
import com.google.common.html.plugin.proto.ProtoOptions;
import com.google.common.html.plugin.proto.ProtoPlanner;
import com.google.common.html.plugin.soy.SoyOptions;
import com.google.common.html.plugin.soy.SoyPlanner;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

/**
 * Generates .js & .java sources from .proto and .soy and compiles .js and .css
 * to optimized bundles.
 */
@Mojo(
    name="generate-sources", defaultPhase=LifecyclePhase.PROCESS_SOURCES,
    // Required because ProtocBundledMojo requires dependency resolution
    // so it can figure out which protobufVersion to use.
    requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class ClosureMavenMojo
extends AbstractMojo {
  @Parameter(
      defaultValue="${project.build.directory}",
      property="outputDir",
      required=true,
      readonly=true)
  private File outputDir;

  @Parameter(defaultValue="${project}", readonly=true, required=true)
  private MavenProject project;


  @Parameter(
      defaultValue="${project.basedir}/src/main/css",
      readonly=true, required=true)
  private File defaultCssSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/js",
      readonly=true, required=true)
  private File defaultJsSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/js",
      readonly=true, required=true)
  private File defaultJsTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/extern/js",
      readonly=true, required=true)
  private File defaultJsExterns;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/js",
      readonly=true, required=true)
  private File defaultJsGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/js",
      readonly=true, required=true)
  private File defaultJsTestGenfiles;

  @Parameter(
      defaultValue="${project.basedir}/src/main/proto",
      readonly=true, required=true)
  private File defaultProtoSource;

  @Parameter(
      defaultValue="${project.basedir}/src/test/proto",
      readonly=true, required=true)
  private File defaultProtoTestSource;

  @Parameter(
      defaultValue="${project.basedir}/src/main/soy",
      readonly=true, required=true)
  private File defaultSoySource;

  /**
   * The dependencies from which to extract supplementary source files.
   */
  @Parameter
  private Extract[] extracts;

  /**
   * Options for the closure-stylesheets compiler.
   * May be specified multiple times to generate different variants, for example
   * one stylesheet for left-to-right languages like English and one for
   * right-to-left languages like Arabic.
   */
  @Parameter
  public CssOptions[] css;

  /**
   * Options for the closure-compiler.
   * May be specified multiple times to generate different variants.
   */
  @Parameter
  public JsOptions[] js;

  /**
   * Options for the protocol buffer compiler.
   */
  @Parameter
  public ProtoOptions proto;

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
  private File javaGenfiles;

  /** The source root for generated {@code .java} test files. */
  @Parameter(
      defaultValue="${project.build.directory}/src/test/java",
      property="javaTestGenfiles",
      required=true)
  private File javaTestGenfiles;

  @Parameter(
      defaultValue="${project.build.directory}/css/{reldir}/compiled{-basename}{-orient}.css",
      readonly=true,
      required=true)
  private String defaultCssOutputPathTemplate;

  /**
   * The output from the CSS class renaming. Provides a map of class
   * names to what they were renammed to.
   * Defaults to target/css/{reldir}/rename-map{-basename}{-orient}.json
   */
  @Parameter(
      defaultValue="${project.build.directory}/css/css-rename-map.json",
      readonly=true,
      required=true)
  private File cssRenameMap;

  @Parameter(
      defaultValue="${project.build.directory}/css/{reldir}/source-map{-basename}{-orient}.json",
      readonly=true,
      required=true)
  private String defaultCssSourceMapPathTemplate;

  /** Path to the file that stores hashes of intermediate outputs. */
  @Parameter(
      defaultValue="${project.build.directory}/closure-maven-plugin-hash-store.json",
      property="hashStoreFile",
      required=true)
  private File hashStoreFile;

  @Parameter(
      defaultValue="${project.build.directory}/src/main/proto/descriptors.pd",
      readonly=true,
      required=true)
  private File defaultMainDescriptorFile;

  @Parameter(
      defaultValue="${project.build.directory}/src/test/proto/descriptors.pd",
      readonly=true,
      required=true)
  private File defaultTestDescriptorFile;

  @Override
  public void execute() throws MojoExecutionException {
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }

    Log log = this.getLog();

    ClosureMavenPluginSubstitutionMapProvider substitutionMapProvider;
    try {
      substitutionMapProvider = new ClosureMavenPluginSubstitutionMapProvider(
          Files.asCharSource(cssRenameMap, Charsets.UTF_8));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to read CSS rename map " + cssRenameMap, ex);
    }

    File jsGenfiles = defaultJsGenfiles;
    File jsTestGenfiles = defaultJsTestGenfiles;
    if (js != null && js.length != 0) {
      JsOptions js0 = js[0];
      if (js0.jsGenfiles != null) {
        jsGenfiles = js0.jsGenfiles;
      }
      if (js0.jsTestGenfiles != null) {
        jsTestGenfiles = js0.jsTestGenfiles;
      }
    }

    HashStore hashStore = null;
    if (hashStoreFile.exists()) {
      try {
        try (InputStream hashStoreIn = new FileInputStream(hashStoreFile)) {
          try (Reader hashStoreReader = new InputStreamReader(
                  hashStoreIn, Charsets.UTF_8)) {
            hashStore = HashStore.read(hashStoreReader, log);
          }
        }
      } catch (IOException ex) {
        log.warn("Failed to read hash store", ex);
      }
    }
    if (hashStore == null) {
      log.debug("Creating empty hash store");
      hashStore = new HashStore();
    }

    CommonPlanner planner;
    try {
      planner = new CommonPlanner(
          log, outputDir, substitutionMapProvider, hashStore);
    } catch (IOException ex) {
      throw new MojoExecutionException("failed to initialize planner", ex);
    }

    planner.genfiles.setStoredObject(new GenfilesDirs(
        outputDir,
        javaGenfiles, javaTestGenfiles,
        jsGenfiles, jsTestGenfiles));

    try {
      new ExtractPlanner(planner, project)
          .plan(extracts != null
                ? ImmutableList.copyOf(extracts)
                : ImmutableList.<Extract>of());
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to plan source file extraction", ex);
    }

    try {
      new CssPlanner(planner)
          .cssRenameMap(cssRenameMap)
          .defaultCssSource(defaultCssSource)
          .defaultCssOutputPathTemplate(defaultCssOutputPathTemplate)
          .defaultCssSourceMapPathTemplate(defaultCssSourceMapPathTemplate)
          .plan(css);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan CSS compile", ex);
    }

    try {
      new ProtoPlanner(planner, protocExecutable())
          .defaultProtoSource(defaultProtoSource)
          .defaultProtoTestSource(defaultProtoTestSource)
          .defaultMainDescriptorFile(defaultMainDescriptorFile)
          .defaultTestDescriptorFile(defaultTestDescriptorFile)
          .plan(proto != null ? proto : new ProtoOptions());
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan proto compile", ex);
    }

    try {
      SoyOptions soyOptions = soy != null ? soy : new SoyOptions();
      new SoyPlanner(planner)
          .defaultSoySource(defaultSoySource)
          .plan(soyOptions);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan proto compile", ex);
    }


    Plan plan = planner.toPlan();
    log.debug("Finished plan.  Executing plan");
    while (!plan.isComplete()) {
      plan.executeOneStep();
    }

    log.debug("Writing hash store to " + hashStoreFile);
    try {
      hashStoreFile.getParentFile().mkdirs();
      try (OutputStream hashStoreOut = new FileOutputStream(hashStoreFile)) {
        try (Writer hashStoreWriter = new OutputStreamWriter(
                hashStoreOut, Charsets.UTF_8)){
          hashStore.write(hashStoreWriter);
        }
      }
    } catch (IOException ex) {
      log.warn("Problem writing hash store", ex);
    }

    log.debug("Writing rename map to " + cssRenameMap);
    try {
      cssRenameMap.getParentFile().mkdirs();
      try (Writer cssRenameOut = Files.asCharSink(cssRenameMap, Charsets.UTF_8)
              .openBufferedStream()) {
        substitutionMapProvider.get()
             .write(OutputRenamingMapFormat.JSON, cssRenameOut);
      }
    } catch (IOException ex) {
      log.warn("Problem writing CSS rename map", ex);
    }
  }


  // For protoc support.

  @Parameter(defaultValue="${plugin}", required=true, readonly=true)
  private PluginDescriptor pluginDescriptor;

  @Component
  private RepositorySystem repositorySystem;

//@Parameter(defaultValue="${project.remoteProjectRepositories}", readonly=true,
//           required=true)
  @Parameter(defaultValue="${project.remoteArtifactRepositories}",
             required=true, readonly=true )
  protected List<ArtifactRepository> remoteRepositories;

  Function<ProtoOptions, File> protocExecutable() {
    return new Function<ProtoOptions, File>() {

      static final String PROTOC_PLUGIN_GROUP_ID = "com.comoyo.maven.plugins";
      static final String PROTOC_PLUGIN_ARTIFACT_ID = "protoc-bundled-plugin";

      @SuppressWarnings("synthetic-access")
      @Override
      public File apply(ProtoOptions options) {
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
        if (options.protobufVersion != null) {
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protobufVersion", options.protobufVersion);
        }
        if (options.protocExec != null) {
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protocExec", options.protocExec);
        }
        Cheats.cheatCall(
            Void.class, ProtocBundledMojo.class,
            protocBundledMojo, "ensureProtocBinaryPresent"
            );
        return Cheats.cheatGet(
            ProtocBundledMojo.class, protocBundledMojo,
            File.class, "protocExec");
      }
    };
  }
}
