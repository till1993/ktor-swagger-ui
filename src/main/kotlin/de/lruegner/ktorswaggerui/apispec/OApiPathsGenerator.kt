package de.lruegner.ktorswaggerui.apispec

import de.lruegner.ktorswaggerui.SwaggerUIPluginConfig
import de.lruegner.ktorswaggerui.documentation.DocumentedRouteSelector
import de.lruegner.ktorswaggerui.documentation.RouteDocumentation
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RootRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.TrailingSlashRouteSelector
import io.swagger.v3.oas.models.Paths

/**
 * Generator for the OpenAPI Paths
 */
class OApiPathsGenerator {

    /**
     * Generate the OpenAPI Paths from the given config and application
     */
    fun generate(config: SwaggerUIPluginConfig, application: Application): Paths {
        return Paths().apply {
            collectRoutes(application, config.getSwaggerUI().swaggerUrl, config.getSwaggerUI().forwardRoot)
                .map { OApiPathGenerator().generate(it, config.automaticUnauthorizedResponses, config.defaultSecurityScheme) }
                .forEach { addPathItem(it.first, it.second) }
        }
    }

    private fun collectRoutes(application: Application, swaggerUrl: String, forwardRoot: Boolean): List<RouteMeta> {
        return allRoutes(application.plugin(Routing))
            .asSequence()
            .map { route ->
                RouteMeta(
                    route = route,
                    method = getMethod(route),
                    path = getPath(route),
                    documentation = getDocumentation(route),
                    protected = isProtected(route)
                )
            }
            .filter { it.path != swaggerUrl }
            .filter { it.path != "$swaggerUrl/{filename}" }
            .filter { it.path != "$swaggerUrl/schemas/{schemaname}" }
            .filter { !forwardRoot || it.path != "/" }
            .toList()
    }

    private fun getDocumentation(route: Route): RouteDocumentation {
        return when (val selector = route.selector) {
            is DocumentedRouteSelector -> selector.documentation
            else -> route.parent?.let { getDocumentation(it) } ?: RouteDocumentation()
        }
    }

    private fun getMethod(route: Route): HttpMethod {
        return (route.selector as HttpMethodRouteSelector).method
    }

    private fun getPath(route: Route): String {
        return when (route.selector) {
            is TrailingSlashRouteSelector -> "/"
            is RootRouteSelector -> ""
            is DocumentedRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            is HttpMethodRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            is AuthenticationRouteSelector -> route.parent?.let { getPath(it) } ?: ""
            else -> (route.parent?.let { getPath(it) } ?: "") + "/" + route.selector.toString()
        }
    }

    private fun isProtected(route: Route): Boolean {
        return when (route.selector) {
            is TrailingSlashRouteSelector -> false
            is RootRouteSelector -> false
            is DocumentedRouteSelector -> route.parent?.let { isProtected(it) } ?: false
            is HttpMethodRouteSelector -> route.parent?.let { isProtected(it) } ?: false
            is AuthenticationRouteSelector -> true
            else -> route.parent?.let { isProtected(it) } ?: false
        }
    }

    private fun allRoutes(root: Route): List<Route> {
        return (listOf(root) + root.children.flatMap { allRoutes(it) })
            .filter { it.selector is HttpMethodRouteSelector }
    }

}