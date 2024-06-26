package co.statu.parsek

import org.pf4j.*
import org.pf4j.PluginManager
import java.nio.file.Path

class ParsekPluginLoader(pluginManager: PluginManager) : JarPluginLoader(pluginManager) {
    override fun loadPlugin(pluginPath: Path, pluginDescriptor: PluginDescriptor): ClassLoader {
        val pluginClassLoader =
            PluginClassLoader(pluginManager, pluginDescriptor, javaClass.classLoader, ClassLoadingStrategy.APD)

        pluginClassLoader.addFile(pluginPath.toFile())

        return pluginClassLoader
    }
}