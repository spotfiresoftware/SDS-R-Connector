import sbt._

object ImplicitUtil extends Build {

  /**
    * Assuming the existence of
    * val mySettings: Seq[sbt.Def.Setting[_]]
    * this implicit conversion allows you to do this
    * val foo = project.settings(mySettings)
    * instead of
    * val foo = project.settings(mySettings: _*)
    * This is helpful because the settings() method expects var args
    * (sbt.Def.Setting[_]*) instead of Seq[sbt.Def.Setting[_]]
    */
  implicit class SeqSettingsProject(p: Project) {

  	  def settings(xs: Seq[sbt.Def.Setting[_]]) = p.settings(xs: _*)
  }
}