package com.github.swagger.pekko

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directives, ExceptionHandler, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.github.swagger.pekko.model._
import com.github.swagger.pekko.samples._
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme.In
import io.swagger.v3.oas.models.servers.Server
import jakarta.ws.rs.{GET, Path}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.BeforeAndAfterAll

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.{Logger, LoggerFactory}

class SwaggerHttpServiceSpec
    extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalatestRouteTest {

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  private val someGlobalHeaderParam: Parameter = new Parameter()
    .name("X-Customer-Header")
    .description("A customer header defined in the global OpenAPI scope")
    .required(true)
    .in(In.HEADER.toString)
    .schema(new Schema().`type`("string"))

  private def swaggerService = new SwaggerHttpService {
    override val apiClasses: Set[Class[_]] = Set(classOf[PetHttpService], classOf[UserHttpService])
    override val basePath = "api"
    override val apiDocsPath = "api-doc"
    override val host = "some.domain.com:12345"
    override val info = Info(description = "desc1",
                   version = "v1.0",
                   title = "title1",
                   termsOfService = "Free and Open",
                   contact = Some(Contact(name = "Alice Smith", url = "http://com.example.com/alicesmith", email = "alice.smith@example.com")),
                   license = Some(License(name = "MIT", url = "https://opensource.org/licenses/MIT")))
  }

  private def swaggerService31 = new SwaggerHttpService {
    override val apiClasses: Set[Class[_]] = Set(classOf[PetHttpService], classOf[UserHttpService])
    override val basePath = "api"
    override val apiDocsPath = "api-doc"
    override val host = "some.domain.com:12345"
    override val schemes = List("https")
    override val specVersion = SpecVersion.V31
    override val info = Info(description = "desc1",
      version = "v1.0",
      title = "title1",
      termsOfService = "Free and Open",
      contact = Some(Contact(name = "Alice Smith", url = "http://com.example.com/alicesmith", email = "alice.smith@example.com")),
      license = Some(License(name = "MIT", url = "https://opensource.org/licenses/MIT")))
  }

  private def swaggerServiceServiceUrls = new SwaggerHttpService {
    override val apiClasses: Set[Class[_]] = Set(classOf[PetHttpService], classOf[UserHttpService])
    override val basePath = "api"
    override val apiDocsPath = "api-doc"
    override val host = "xyz.domain.com"
    override val serverURLs = Seq("https://some.domain.com:12345")
    override val info = Info(description = "desc1",
      version = "v1.0",
      title = "title1",
      termsOfService = "Free and Open",
      contact = Some(Contact(name = "Alice Smith", url = "http://com.example.com/alicesmith", email = "alice.smith@example.com")),
      license = Some(License(name = "MIT", url = "https://opensource.org/licenses/MIT")))
  }

  @Path("user")
  private case class UserRoute() {

    def apiRoute: Route = pathPrefix("user") {
      concat(
        path("1") {
          get(firstApi)
        },
        path("2") {
          get(secondApi)
        },
      )
    }

    @GET
    @Path("1")
    private def firstApi: Route = {
      complete("Ok")
    }

    @GET
    @Path("2")
    private def secondApi: Route = {
      complete("Ok")
    }

  }

  class PrivateUserSwaggerRoute(domain: String,
                                httpSchemes: List[String]) extends Directives with SwaggerGenerator {

    val log: Logger = LoggerFactory.getLogger(classOf[PrivateUserSwaggerRoute])

    override val apiClasses: Set[Class[_]] = Set(
      classOf[UserRoute]
    )

    override val host: String = domain

    override val schemes: List[String] = httpSchemes

    override val apiDocsPath = "swagger"

    //When commented swagger is generated properly
    override val unwantedDefinitions: Seq[String] = Seq("Function1", "Function1RequestContextFutureRouteResult")

    //For printing to logs error thrown by generator
    private def exceptionHandler: ExceptionHandler = ExceptionHandler {
      case e: NullPointerException =>
        log.error("NullPointerException occurred", e)
        complete(StatusCodes.InternalServerError, "NullPointerException")

      case e: Exception =>
        log.error("Unexpected exception occurred", e)
        complete(StatusCodes.InternalServerError, "InternalServerError")
    }

    def apiRoute: Route = path("swagger") {
      handleExceptions(exceptionHandler) {
        //Just always return swagger generation
        complete(generateSwaggerJson)
      }
    }

  }

  private implicit val formats: org.json4s.Formats = org.json4s.DefaultFormats

  "The SwaggerHttpService" when {
    "accessing the root doc path" should {
      "return the basic set of api info" in {
        Get(s"/${swaggerService.apiDocsPath}/swagger.json") ~> swaggerService.routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
          val str = responseAs[String]
          val response = parse(str)
          (response \ "openapi").extract[String] shouldEqual "3.0.1"
          (response \ "info" \ "description").extract[String] shouldEqual swaggerService.info.description
          (response \ "info" \ "title").extract[String] shouldEqual swaggerService.info.title
          (response \ "info" \ "termsOfService").extract[String] shouldEqual swaggerService.info.termsOfService
          (response \ "info" \ "version").extract[String] shouldEqual swaggerService.info.version
          (response \ "info" \ "contact").extract[Option[Contact]] shouldEqual swaggerService.info.contact
          (response \ "info" \ "license").extract[Option[License]] shouldEqual swaggerService.info.license
          val servers = (response \ "servers").extract[JArray]
          servers.arr should have size 1
          (servers.arr.head \ "url").extract[String] shouldEqual "http://some.domain.com:12345/api/"
        }
      }

      "return the basic set of api info (spec 3.1)" in {
        Get(s"/${swaggerService.apiDocsPath}/swagger.json") ~> swaggerService31.routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
          val str = responseAs[String]
          val response = parse(str)
          (response \ "openapi").extract[String] shouldEqual "3.1.0"
          (response \ "info" \ "description").extract[String] shouldEqual swaggerService.info.description
          (response \ "info" \ "title").extract[String] shouldEqual swaggerService.info.title
          (response \ "info" \ "termsOfService").extract[String] shouldEqual swaggerService.info.termsOfService
          (response \ "info" \ "version").extract[String] shouldEqual swaggerService.info.version
          (response \ "info" \ "contact").extract[Option[Contact]] shouldEqual swaggerService.info.contact
          (response \ "info" \ "license").extract[Option[License]] shouldEqual swaggerService.info.license
          val servers = (response \ "servers").extract[JArray]
          servers.arr should have size 1
          (servers.arr.head \ "url").extract[String] shouldEqual "https://some.domain.com:12345/api/"
        }
      }

      "return the servers based on serverURLs" in {
        Get(s"/${swaggerService.apiDocsPath}/swagger.json") ~> swaggerServiceServiceUrls.routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
          val str = responseAs[String]
          val response = parse(str)
          (response \ "openapi").extract[String] shouldEqual "3.0.1"
          (response \ "info" \ "description").extract[String] shouldEqual swaggerService.info.description
          (response \ "info" \ "title").extract[String] shouldEqual swaggerService.info.title
          (response \ "info" \ "termsOfService").extract[String] shouldEqual swaggerService.info.termsOfService
          (response \ "info" \ "version").extract[String] shouldEqual swaggerService.info.version
          (response \ "info" \ "contact").extract[Option[Contact]] shouldEqual swaggerService.info.contact
          (response \ "info" \ "license").extract[Option[License]] shouldEqual swaggerService.info.license
          val servers = (response \ "servers").extract[JArray]
          servers.arr should have size 1
          (servers.arr.head \ "url").extract[String] shouldEqual "https://some.domain.com:12345/api/"
        }
      }
    }

    "concatenated with other route" should {
      "not affect matching" in {
        val myRoute = path("p1" / "p2") {
          delete {
            complete("ok")
          }
        }
        Delete("/p1/wrong") ~> Route.seal(myRoute ~ swaggerService.routes) ~> check {
          status shouldNot be(StatusCodes.MethodNotAllowed)
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    "defining a derived service" should {
      "set the basePath" in {
        swaggerService.basePath should equal ("api")
      }
      "set the apiDocsPath" in {
        swaggerService.apiDocsPath should equal ("api-doc")
      }
      "prependSlashIfNecessary adds a slash" in {
        SwaggerHttpService.prependSlashIfNecessary("/api-doc") should equal ("/api-doc")
      }
      "prependSlashIfNecessary does not need to add a slash" in {
        SwaggerHttpService.prependSlashIfNecessary("/api-doc") should equal ("/api-doc")
      }
      "removeInitialSlashIfNecessary removes a slash" in {
        SwaggerHttpService.removeInitialSlashIfNecessary("/api-doc") should equal ("api-doc")
      }
      "removeInitialSlashIfNecessary does not need to remove a slash" in {
        SwaggerHttpService.removeInitialSlashIfNecessary("api-doc") should equal ("api-doc")
      }
    }

    "defining an apiDocsPath" should {
      def swaggerService(testPath: String) = new SwaggerHttpService {
        override val apiClasses: Set[Class[_]] = Set(classOf[UserHttpService])
        override val apiDocsPath = testPath
      }
      def performGet(testPath: String) = {
        Get(s"/${SwaggerHttpService.removeInitialSlashIfNecessary(testPath)}/swagger.json") ~> swaggerService(testPath).routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
        }
      }
      def performYamlGet(testPath: String) = {
        Get(s"/${SwaggerHttpService.removeInitialSlashIfNecessary(testPath)}/swagger.yaml") ~> swaggerService(testPath).routes ~> check {
          handled shouldBe true
          contentType.toString() shouldBe CustomMediaTypes.`text/vnd.yaml`.toString()
        }
      }
      "support root slash" in {
        performGet("/arbitrary")
        performYamlGet("/arbitrary")
      }
      "support root slash and path elements" in {
        performGet("/arbitrary/path/to/docs")
        performYamlGet("/arbitrary/path/to/docs")
      }
      "support no root slash" in {
        performGet("arbitrary")
        performYamlGet("arbitrary")
      }
      "support no root slash and path elements" in {
        performGet("arbitrary/path/to/docs")
        performYamlGet("arbitrary/path/to/docs")
      }
    }

    "defining vendor extensions" should {
      val swaggerService = new SwaggerHttpService {
        override val apiClasses: Set[Class[_]] = Set(classOf[UserHttpService])
        override val apiDocsPath = "api-doc"
        override val vendorExtensions = ListMap("x-service-name" -> "ums",
                                                "x-service-version" -> "v1",
                                                "x-service-interface" -> "rest")
      }
      "return all vendor extensions" in {
        Get(s"/${swaggerService.apiDocsPath}/swagger.json") ~> swaggerService.routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
          val str = responseAs[String]
          val response = parse(str)
          (response \ "x-service-name").extract[String] shouldEqual "ums"
          (response \ "x-service-version").extract[String] shouldEqual "v1"
          (response \ "x-service-interface").extract[String] shouldEqual "rest"
        }
      }
    }

    "defining relative paths" should {
      val swaggerService = new SwaggerHttpService {
        override val apiClasses: Set[Class[_]] = Set(classOf[UserHttpService])
        override val apiDocsPath = "api-doc"
        override val host = ""
        override val basePath = "test/path"
        override val schemes = List.empty[String]
      }
      "handle path properly" in {
        Get(s"/${swaggerService.apiDocsPath}/swagger.json") ~> swaggerService.routes ~> check {
          handled shouldBe true
          contentType shouldBe ContentTypes.`application/json`
          val str = responseAs[String]
          val response = parse(str)
          ((response \ "servers")(0) \ "url").extract[String] shouldEqual "/test/path/"
        }
      }
    }

    "conversion of scala collections to java" should {
      "return mutable list" in {
        val jlist = swaggerService.asJavaMutableList(List("http"))
        jlist.add("extra")
        jlist.asScala.toSet shouldEqual Set("http", "extra")
      }
      "return mutable map" in {
        val jmap = swaggerService.asJavaMutableMap(Map("scheme" -> "http"))
        jmap.put("extraKey", "extraValue")
        jmap.asScala.toMap shouldEqual Map(("scheme" -> "http"), ("extraKey" -> "extraValue"))
      }
      "mean that getSecurity returns a mutable list" in {
        swaggerService.swaggerConfig.getSecurity.add(new SecurityRequirement)
      }
      "mean that getExtensions returns a mutable map" in {
        swaggerService.swaggerConfig.getExtensions.put("fakeExtension", new Object)
      }
      "mean that getServers returns a mutable list" in {
        swaggerService.swaggerConfig.getServers.add(new Server)
      }
    }

    "SwaggerHttpService" should {
      "not fail when there are private methods" in {
        val route = new PrivateUserSwaggerRoute("abc.com", List("http"))
        val json = route.generateSwaggerJson
        json should not be empty
      }
    }
  }
}
