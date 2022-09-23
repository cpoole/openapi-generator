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
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.checkerframework.checker.units.qual.A;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModelFactory;
import org.openapitools.codegen.CodegenModelType;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GoGinServerCodegen extends AbstractGoCodegen {

    private final Logger LOGGER = LoggerFactory.getLogger(GoGinServerCodegen.class);

    protected String apiVersion = "1.0.0";
    protected int serverPort = 8080;
    protected String projectName = "openapi-server";
    protected String apiPath = "go";
    public String sup = "foo";

    public GoGinServerCodegen() {
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
        embeddedTemplateDir = templateDir = "go-gin-server";

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

    class ExtendedCodegenOperation extends CodegenOperation {
      String pascalCasePath;
      String httpMethodCAPS;
      List<String> routeMiddlewares;
      List<String> goQueryParams;
      private final Logger LOGGER = LoggerFactory.getLogger(ExtendedCodegenOperation.class);


      public ExtendedCodegenOperation(CodegenOperation o, List<String> middlewareNames) {
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

        //LOGGER.warn(o.);
        for(CodegenParameter param : this.queryParams) {
          this.goQueryParams.add(String.format("%s %s", param.paramName, param.dataType));
        }

        this.routeMiddlewares = middlewareNames;
      }

      /**
       * Get the path as a PascalCased String
       *
       * @return the substring
       */
      private String pathAsPascalCase() {
        Stream<String> words = Arrays.stream(path.split("/"));
        return words.map(StringUtils::capitalize).reduce("", (accumulated, word) -> accumulated + word);
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
      List<String> middlewares = new ArrayList<String>();
      for (SecurityRequirement security : operation.getSecurity()) {
        for(String key : security.keySet()) {
          middlewares.add(formattedSecuritySchemeName(key));
        }
      }

      return new ExtendedCodegenOperation(op, middlewares);
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
            setPackageName("openapi");
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

        ArrayList<String> routeMiddlewares = new ArrayList<String>();
        Map<String, SecurityScheme> securitySchemeMap = openAPI.getComponents() != null ? openAPI.getComponents().getSecuritySchemes() : null;
        if (securitySchemeMap != null) {
          for (Map.Entry<String, SecurityScheme> security : securitySchemeMap.entrySet()) {
           routeMiddlewares.add(formattedSecuritySchemeName(security.getKey()));
          }
        }

        additionalProperties.put("routeMiddlewares", routeMiddlewares);

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
        return "go-gin-server";
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
