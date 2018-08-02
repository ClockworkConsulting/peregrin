package dk.cwconsult.peregrin.core

case class AppliedMigrations(migrations: Vector[Migration]) {

  def count: Int =
    migrations.size

}
