package com.github.swagger.pekko.model

import com.github.swagger.pekko.SwaggerHttpService
import com.github.swagger.pekko.samples.{PetHttpService, UserHttpService}
import io.swagger.v3.oas.models.info.{Contact => SwaggerContact, Info => SwaggerInfo, License => SwaggerLicense}
import io.swagger.v3.oas.models.media.Schema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ModelToolSpec extends AnyWordSpec with Matchers {

  "swaggerToScala" should {
    "support roundtrip" in {
      val swaggerInfo = new SwaggerInfo().version("v1").description("desc1").termsOfService("TOS1").title("title1")
        .contact(new SwaggerContact().name("contact-name1").email("contact@example.com").url("http://example.com/about"))
        .license(new SwaggerLicense().name("example-license").url("http://example.com/license"))
      val info: Info = swagger2scala(swaggerInfo)
      scala2swagger(info) shouldEqual swaggerInfo
    }
  }

  "asScala" should {
    "handle null" in {
      asScala(null.asInstanceOf[java.util.Map[String, Schema[_]]]) shouldEqual Map.empty[String, Schema[_]]
      asScala(null.asInstanceOf[java.util.Map[String, String]]) shouldEqual Map.empty[String, String]
      asScala(null.asInstanceOf[java.util.Set[String]]) shouldEqual Set.empty[String]
      asScala(null.asInstanceOf[java.util.Set[Schema[_]]]) shouldEqual Set.empty[Schema[_]]
      asScala(null.asInstanceOf[java.util.List[String]]) shouldEqual List.empty[String]
      asScala(null.asInstanceOf[java.util.List[Schema[_]]]) shouldEqual List.empty[Schema[_]]
      asScala(null.asInstanceOf[java.util.Optional[String]]) shouldEqual None
      asScala(null.asInstanceOf[java.util.Optional[Schema[_]]]) shouldEqual None
    }
    "handle simple java map" in {
      val swaggerService = new SwaggerHttpService {
        override val apiClasses: Set[Class[_]] = Set(classOf[PetHttpService], classOf[UserHttpService])
      }
      swaggerService.filteredSwagger should not be (null)
//      swaggerService.filteredSwagger.getComponents should not be (null)
//      val definitions = swaggerService.filteredSwagger.getComponents.getSchemas
//      definitions should not be null
//      definitions should have size 4
//      val smap = asScala(definitions)
//      smap should contain theSameElementsAs definitions.asScala
    }
  }
}