package com.github.swagger.pekko.samples

import jakarta.ws.rs.Path

abstract class TestApiWithNoAnnotation

@Path("/test")
object TestApiWithObject {
  //@ApiOperation(value = "testApiOperation", httpMethod = "GET")
  def testOperation = {}
}

