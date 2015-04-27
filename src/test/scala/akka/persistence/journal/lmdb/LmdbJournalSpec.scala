package akka.persistence.journal.lmdb

import java.io.File

import akka.persistence.journal.JournalSpec
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

class LmdbJournalSpec extends JournalSpec {
  lazy val config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "lmdb-journal"
      |akka.persistence.snapshot-store.local.dir = "target/snapshots"
      |akka.persistence.publish-plugin-commands = on
      |akka.persistence.publish-confirmations = on
      |lmdb-journal.dir = "target/journal"
    """.stripMargin)


  protected override def afterAll(): Unit = {
    cleanUp()
    super.afterAll()
  }


  protected override def beforeAll(): Unit = {
    cleanUp()
    super.beforeAll()
  }

  def cleanUp() = {
    FileUtils.deleteDirectory(new File(config.getString("akka.persistence.snapshot-store.local.dir")))
    FileUtils.deleteDirectory(new File(config.getString("lmdb-journal.dir")))
  }

}
