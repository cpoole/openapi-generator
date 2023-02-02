/*
 * Copyright 2018 OpenAPI-Generator Contributors (https://openapi-generator.tech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openapitools.codegen.languages;

import com.github.jknack.handlebars.internal.lang3.StringUtils;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Operation;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GoGinBirdServerCodegen extends AbstractGoCodegen {

  private final Logger LOGGER = LoggerFactory.getLogger(GoGinBirdServerCodegen.class);

  private final String birdAuthMiddlewareName = "bird_auth";

  protected String apiVersion = "1.0.0";
  protected int serverPort = 8080;
  protected String projectName = "openapi-server";
  protected String apiPath = "generated-go-server";

  public GoGinBirdServerCodegen() {
    super();

    modifyFeatureSet(features -> features
            .includeDocumentationFeatures(DocumentationFeature.Readme)
            .wireFormatFeatures(EnumSet.of(WireFormatFeature.JSON))
            .securityFeatures(EnumSet.of(
                    SecurityFeature.BearerToken,
                    SecurityFeature.BasicAuth
            ))
            .excludeGlobalFeatures(
                    GlobalFeature.XMLStructureDefinitions,
                    GlobalFeature.Callbacks,
                    GlobalFeature.LinkObjects,
                    GlobalFeature.ParameterStyling
            )
            .excludeSchemaSupportFeatures(
                    SchemaSupportFeature.Polymorphism
            )
            .excludeParameterFeatures(
                    ParameterFeature.Cookie
            )
    );

    // set the output folder here
    outputFolder = "generated-code/go";

    /*
     * Models.  You can write model files using the modelTemplateFiles map.
     * if you want to create one template for file, you can do so here.
     * for multiple files for model, just put another entry in the `modelTemplateFiles` with
     * a different extension
     */
    modelTemplateFiles.put(
            "model.mustache",
            ".go");

    /*
     * Template Location.  This is the location which templates will be read from.  The generator
     * will use the resource stream to attempt to read the templates.
     */
    embeddedTemplateDir = templateDir = "go-gin-bird-server";

    /*
     * Reserved words.  Override this with reserved words specific to your language
     */
    setReservedWordsLowerCase(
            Arrays.asList(
                    // data type
                    "string", "bool", "uint", "uint8", "uint16", "uint32", "uint64",
                    "int", "int8", "int16", "int32", "int64", "float32", "float64",
                    "complex64", "complex128", "rune", "byte", "uintptr",

                    "break", "default", "func", "interface", "select",
                    "case", "defer", "go", "map", "struct",
                    "chan", "else", "goto", "package", "switch",
                    "const", "fallthrough", "if", "range", "type",
                    "continue", "for", "import", "return", "var", "error", "nil")
            // Added "error" as it's used so frequently that it may as well be a keyword
    );

    cliOptions.add(new CliOption("apiPath", "Name of the folder that contains the Go source code")
            .defaultValue(apiPath));

    CliOption optServerPort = new CliOption("serverPort", "The network port the generated server binds to");
    optServerPort.setType("int");
    optServerPort.defaultValue(Integer.toString(serverPort));
    cliOptions.add(optServerPort);

    cliOptions.add(CliOption.newBoolean(CodegenConstants.ENUM_CLASS_PREFIX, CodegenConstants.ENUM_CLASS_PREFIX_DESC));
  }

  public static String pathSanitizer(String input) {
    Stream<String> words = Arrays.stream(input.split("-"));
    return words.map(StringUtils::capitalize).reduce("", (accumulated, word) -> accumulated + word);
  }

  class ExtendedCodegenOperation extends CodegenOperation {
    String pascalCasePath;
    String httpMethodCAPS;
    List<String> routeMiddlewares;
    List<String> goQueryParams;

    Set<String> extraImports;

    Boolean hasBirdAuthMiddleware;

    String interfaceMethodString;

    String routerCallString;

    // the entry into the auth route to allowed roles map
    String routeAuthMapString;

    // the string representing the golang object to bind to the parameters
    String queryParamStructString;

    private final Logger LOGGER = LoggerFactory.getLogger(ExtendedCodegenOperation.class);


    public ExtendedCodegenOperation(CodegenOperation o, List<SecurityRequirement> securityMiddlewares) throws Exception {
      super();

      this.responseHeaders.addAll(o.responseHeaders);
      this.hasAuthMethods = o.hasAuthMethods;
      this.hasConsumes = o.hasConsumes;
      this.hasProduces = o.hasProduces;
      this.hasParams = o.hasParams;
      this.hasOptionalParams = o.hasOptionalParams;
      this.hasRequiredParams = o.hasRequiredParams;
      this.returnTypeIsPrimitive = o.returnTypeIsPrimitive;
      this.returnSimpleType = o.returnSimpleType;
      this.subresourceOperation = o.subresourceOperation;
      this.isMap = o.isMap;
      this.isArray = o.isArray;
      this.isMultipart = o.isMultipart;
      this.isResponseBinary = o.isResponseBinary;
      this.isResponseFile = o.isResponseFile;
      this.hasReference = o.hasReference;
      this.isRestfulIndex = o.isRestfulIndex;
      this.isRestfulShow = o.isRestfulShow;
      this.isRestfulCreate = o.isRestfulCreate;
      this.isRestfulUpdate = o.isRestfulUpdate;
      this.isRestfulDestroy = o.isRestfulDestroy;
      this.isRestful = o.isRestful;
      this.isDeprecated = o.isDeprecated;
      this.isCallbackRequest = o.isCallbackRequest;
      this.uniqueItems = o.uniqueItems;
      this.path = o.path;
      this.operationId = o.operationId;
      this.returnType = o.returnType;
      this.returnFormat = o.returnFormat;
      this.httpMethod = o.httpMethod;
      this.returnBaseType = o.returnBaseType;
      this.returnContainer = o.returnContainer;
      this.summary = o.summary;
      this.unescapedNotes = o.unescapedNotes;
      this.notes = o.notes;
      this.baseName = o.baseName;
      this.defaultResponse = o.defaultResponse;
      this.discriminator = o.discriminator;
      this.consumes = o.consumes;
      this.produces = o.produces;
      this.prioritizedContentTypes = o.prioritizedContentTypes;
      this.servers = o.servers;
      this.bodyParam = o.bodyParam;
      this.allParams = o.allParams;
      this.bodyParams = o.bodyParams;
      this.pathParams = o.pathParams;
      this.queryParams = o.queryParams;
      this.headerParams = o.headerParams;
      this.formParams = o.formParams;
      this.cookieParams = o.cookieParams;
      this.requiredParams = o.requiredParams;
      this.optionalParams = o.optionalParams;
      this.authMethods = o.authMethods;
      this.tags = o.tags;
      this.responses = o.responses;
      this.callbacks = o.callbacks;
      this.imports = o.imports;
      this.examples = o.examples;
      this.requestBodyExamples = o.requestBodyExamples;
      this.externalDocs = o.externalDocs;
      this.vendorExtensions = o.vendorExtensions;
      this.nickname = o.nickname;
      this.operationIdOriginal = o.operationIdOriginal;
      this.operationIdLowerCase = o.operationIdLowerCase;
      this.operationIdCamelCase = o.operationIdCamelCase;
      this.operationIdSnakeCase = o.operationIdSnakeCase;
      this.pascalCasePath = this.pathAsPascalCase();
      this.goQueryParams = new ArrayList<>();
      this.httpMethodCAPS = o.httpMethod.toUpperCase();
      this.hasBirdAuthMiddleware = Boolean.FALSE;
      this.routeMiddlewares = new ArrayList<String>();
      this.extraImports = new HashSet<String>();

      String httpMethod = StringUtils.capitalize(this.httpMethod.toLowerCase());

      //LOGGER.warn(o.);
      for(CodegenParameter param : this.queryParams) {
        this.goQueryParams.add(String.format("%s %s", param.paramName, param.dataType));
      }

      this.routerCallString = String.format("%s%s := func(ctx *gin.Context) {\n", this.pascalCasePath, httpMethod);

      // managing bird auth middleware
      if (securityMiddlewares == null && !Objects.equals(this.path, "/healthcheck")) {
        throw new Exception("non-healcheck endpoints must have security");
      }

      if (securityMiddlewares != null) {

        List<String> middlewares = new ArrayList<String>();
        for (SecurityRequirement security : securityMiddlewares) {
          for (String key : security.keySet()) {
            middlewares.add(formattedSecuritySchemeName(key));
          }
        }


        for (SecurityRequirement securityCollection : securityMiddlewares) {
          for (String rawSecurityName : securityCollection.keySet()) {
            String middlewareName = formattedSecuritySchemeName(rawSecurityName);
            if (middlewareName.equals(formattedSecuritySchemeName(birdAuthMiddlewareName))) {
              this.routeAuthMapString = String.format("\"%s\": ", this.path);

              this.hasBirdAuthMiddleware = Boolean.TRUE;

              StringBuilder roleSliceBuilder = new StringBuilder("{");
              for (String role : securityCollection.get(rawSecurityName)) {
                roleSliceBuilder.append(String.format("authStructs.AUTH_ROLE_%s(),", StringUtils.capitalize(role)));
              }
              roleSliceBuilder.append("},");
              this.routeAuthMapString += roleSliceBuilder.toString();
            } else {
              this.routeMiddlewares.add(middlewareName);
            }
          }
        }
      }


      if (httpMethodCAPS.equals("PUT") || httpMethodCAPS.equals("POST")) {
        this.interfaceMethodString = String.format("%s%s(ctx *gin.Context, body %s) (%s, error)",this.pascalCasePath, httpMethod, this.bodyParam.baseName, this.returnType);
        this.routerCallString += String.format("bodyStruct := %s{}\n", this.bodyParam.dataType) +
                "err := ctx.BindJSON(&bodyStruct)\n" +
                "if err != nil {\n" +
                "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                "  return\n" +
                "}\n" +
                String.format("result, err := handlers.%s%s(ctx, bodyStruct)\n", this.pascalCasePath, httpMethod) +
                "if err != nil {\n" +
                "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                "  return\n" +
                "}\n" +
                "ctx.JSON(http.StatusOK, result)\n}\n";
      } else {
        String queryParamStructName = String.format("Param%s%s", httpMethod, this.pascalCasePath);
        this.queryParamStructString = String.format("type %s struct {\n", queryParamStructName);

        for(CodegenParameter param : this.queryParams) {
          String paramType = param.dataType;
          if (!param.required) {
            paramType = String.format("*%s",paramType);
          }
          this.queryParamStructString += String.format("\t%s %s `form:\"%s\"`\n", StringUtils.capitalize(param.paramName), paramType, param.paramName);
        }

        this.queryParamStructString += "}";

        this.interfaceMethodString = String.format("%s%s(ctx *gin.Context, params %s) (%s, error)",this.pascalCasePath, httpMethod, queryParamStructName, this.returnType);

        String paramParse = String.format(
                "params := %s{}\n" +
                        "err := ctx.BindQuery(&params)\n" +
                        "if err != nil {\n" +
                        "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                        "  return\n" +
                        "}\n", queryParamStructName);


        this.routerCallString += paramParse +
                String.format("result, err := handlers.%s%s(ctx, params)\n", this.pascalCasePath, httpMethod) +
                "if err != nil {\n" +
                "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                "  return\n" +
                "}\n" +
                "ctx.JSON(http.StatusOK, result)\n}\n";
      }
    }

    private Pair<String,ArrayList<String>> parseParam(CodegenParameter param) {
      ArrayList<String> strconvImport = new ArrayList<String>(Collections.singletonList("\"strconv\""));
      switch (param.dataType) {
        case "string":
          return new Pair<>(String.format("%s := ctx.Param(\"%s\")\n", param.paramName, param.paramName), new ArrayList<>());
        case "int32":
          return new Pair<>(String.format("%s64, err := strconv.ParseInt(ctx.Param(\"%s\"), 10, 32)\n" +
                  "if err != nil {\n" +
                  "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                  "  return\n" +
                  "}\n" +
                  "%s := int32(%s64)\n", param.paramName, param.paramName, param.paramName,param.paramName), strconvImport);
        case "int64":
          return new Pair<>(String.format("%s, err := strconv.ParseInt(ctx.Param(\"%s\"), 10, 64)\n" +
                  "if err != nil {\n" +
                  "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                  "  return\n" +
                  "}\n", param.paramName,param.paramName), strconvImport);
        case "array":
        case "object":
        case "float32":
          return new Pair<>(String.format("%s64, err := strconv.ParseFloat(ctx.Param(\"%s\"), 32)\n" +
                  "if err != nil {\n" +
                  "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                  "  return\n" +
                  "}\n" +
                  "%s := float32(%s64)\n", param.paramName, param.paramName, param.paramName,param.paramName), strconvImport);
        case "float64":
          return new Pair<>(String.format("%s64, err := strconv.ParseFloat(ctx.Param(\"%s\"), 32)\n" +
                  "if err != nil {\n" +
                  "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                  "  return\n" +
                  "}\n", param.paramName,param.paramName), strconvImport);
        case "bool":
          return new Pair<>(String.format("%s, err := strconv.ParseBool(ctx.Param(\"%s\"))\n" +
                  "if err != nil {\n" +
                  "  ctx.AbortWithStatusJSON(http.StatusInternalServerError, gin.H{\"error\": err.Error})\n" +
                  "  return\n" +
                  "}\n", param.paramName, param.paramName), strconvImport);

      }

      return new Pair<>("", new ArrayList<>());
    }


    /**
     * Get the path as a PascalCased String
     *
     * @return the substring
     */
    private String pathAsPascalCase() {
      Stream<String> words = Arrays.stream(path.split("/"));
      return words.map(GoGinBirdServerCodegen::pathSanitizer).reduce("", (accumulated, word) -> accumulated + word);
    }
  }

  /**
   * Convert OAS Operation object to Codegen Operation object
   *
   * @param httpMethod HTTP method
   * @param operation  OAS operation object
   * @param path       the path of the operation
   * @param servers    list of servers
   * @return Codegen Operation object
   */
  @Override
  public CodegenOperation fromOperation(String path,
                                        String httpMethod,
                                        Operation operation,
                                        List<Server> servers) {
    CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
    try {
      return new ExtendedCodegenOperation(op, operation.getSecurity());
    } catch (Exception e) {
      return op;
    }
  }

  @Override
  public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
    objs = super.postProcessOperationsWithModels(objs, allModels);

    OperationMap operations = objs.getOperations();
    List<CodegenOperation> operationList = operations.getOperation();
    for (CodegenOperation op : operationList) {
      if (op.path != null) {
        op.path = op.path.replaceAll("\\{(.*?)\\}", ":$1");
      }
    }
    return objs;
  }

  @Override
  public void processOpts() {
    super.processOpts();

    if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
      setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME));
    } else {
      setPackageName("genserver");
      additionalProperties.put(CodegenConstants.PACKAGE_NAME, this.packageName);
    }

    /*
     * Additional Properties.  These values can be passed to the templates and
     * are available in models, apis, and supporting files
     */
    if (additionalProperties.containsKey("apiVersion")) {
      this.apiVersion = (String) additionalProperties.get("apiVersion");
    } else {
      additionalProperties.put("apiVersion", apiVersion);
    }

    if (additionalProperties.containsKey("serverPort")) {
      this.serverPort = Integer.parseInt((String) additionalProperties.get("serverPort"));
    } else {
      additionalProperties.put("serverPort", serverPort);
    }

    if (additionalProperties.containsKey("apiPath")) {
      this.apiPath = (String) additionalProperties.get("apiPath");
    } else {
      additionalProperties.put("apiPath", apiPath);
    }

    if (additionalProperties.containsKey(CodegenConstants.ENUM_CLASS_PREFIX)) {
      setEnumClassPrefix(Boolean.parseBoolean(additionalProperties.get(CodegenConstants.ENUM_CLASS_PREFIX).toString()));
      if (enumClassPrefix) {
        additionalProperties.put(CodegenConstants.ENUM_CLASS_PREFIX, true);
      }
    }

    // bird auth middleware is a special middlware. We're going to inject that middleware independently
    ArrayList<String> routeMiddlewares = new ArrayList<String>();
    Map<String, SecurityScheme> securitySchemeMap = openAPI.getComponents() != null ? openAPI.getComponents().getSecuritySchemes() : null;
    if (securitySchemeMap != null) {
      for (Map.Entry<String, SecurityScheme> security : securitySchemeMap.entrySet()) {
        if (!Objects.equals(security.getKey(), birdAuthMiddlewareName)) {
          routeMiddlewares.add(formattedSecuritySchemeName(security.getKey()));
        }
      }
    }

    additionalProperties.put("uniqueRouteMiddlewares", routeMiddlewares);

    modelPackage = packageName;
    apiPackage = packageName;

    /*
     * Supporting Files.  You can write single files for the generator with the
     * entire object tree available.  If the input file has a suffix of `.mustache
     * it will be processed by the template engine.  Otherwise, it will be copied
     */
    supportingFiles.add(new SupportingFile("openapi.mustache", "api", "openapi.yaml"));
    supportingFiles.add(new SupportingFile("routers.mustache", apiPath, "routers.go"));

  }

  protected String formattedSecuritySchemeName(String scheme) {
    String[] rawParts = scheme.split("_");
    List<String> rawPartsCapitalized = Arrays.stream(rawParts).map(StringUtils::capitalize).collect(Collectors.toList());

    return String.join("", rawPartsCapitalized);
  }

  @Override
  public String apiPackage() {
    return apiPath;
  }

  /**
   * Configures the type of generator.
   *
   * @return the CodegenType for this generator
   * @see org.openapitools.codegen.CodegenType
   */
  @Override
  public CodegenType getTag() {
    return CodegenType.SERVER;
  }

  /**
   * Configures a friendly name for the generator.  This will be used by the generator
   * to select the library with the -g flag.
   *
   * @return the friendly name for the generator
   */
  @Override
  public String getName() {
    return "go-gin-bird-server";
  }

  /**
   * Returns human-friendly help for the generator.  Provide the consumer with help
   * tips, parameters here
   *
   * @return A string value for the help message
   */
  @Override
  public String getHelp() {
    return "Generates a Go server library with the gin framework using OpenAPI-Generator." +
            "By default, it will also generate service classes.";
  }

  /**
   * Location to write api files.  You can use the apiPackage() as defined when the class is
   * instantiated
   */
  @Override
  public String apiFileFolder() {
    return outputFolder + File.separator + apiPackage().replace('.', File.separatorChar);
  }

  @Override
  public String modelFileFolder() {
    return outputFolder + File.separator + apiPackage().replace('.', File.separatorChar);
  }

}
