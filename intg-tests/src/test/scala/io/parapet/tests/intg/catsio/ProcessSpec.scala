package io.parapet.tests.intg.catsio

import cats.effect.IO
import io.parapet.testutils.BasicCatsIOSpec
import io.parapet.testutils.tags.CatsTest

@CatsTest
class ProcessSpec extends io.parapet.tests.intg.ProcessSpec[IO] with BasicCatsIOSpec