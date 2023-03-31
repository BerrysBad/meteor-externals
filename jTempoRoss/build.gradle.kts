



version = "1.0.1"

project.extra["PluginName"] = "jTempoRoss"
project.extra["PluginDescription"] = "This is a automated plugin for tempoross."

val pluginClass by rootProject.extra { "meteor.jTempoRossPlugin" }


tasks {
    jar {
        manifest {

            attributes(mapOf(
                "Main-Class" to pluginClass,
                "Plugin-Version" to project.version,
                "Plugin-Id" to nameToId(project.extra["PluginName"] as String),
                "Plugin-Description" to project.extra["PluginDescription"],
            ))
        }
    }
}
