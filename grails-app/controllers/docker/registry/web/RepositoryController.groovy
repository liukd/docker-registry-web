package docker.registry.web

import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Value

class RepositoryController {
  @Value('${registry.url}')
  String registryUrl

  def restService

  def index() {
    def repos = restService.get('_catalog').json.repositories.collect { name ->
      def tags = getTags(name, false)
      [name: name, tags: tags.count { it.exists }]
    }
    [repos: repos]
  }

  def tags() {
    def name = URLDecoder.decode(params.id, 'UTF-8')
    def tags = getTags(name, true)
    if (!tags.count { it.exists })
      redirect action: 'index'
    [tags: tags]
  }

  private def getTags(name, boolean deep = true) {
    def resp = restService.get("${name}/tags/list").json
    def tags = resp.tags.collect { tag ->
      def manifest = restService.get("${name}/manifests/${tag}")
      def exists = manifest.statusCode.'2xxSuccessful'
      BigInteger size = 0
      if (deep && exists) {
        size = manifest.json.fsLayers.sum { layer ->
          def digest = layer.blobSum
          restService.headLength("${name}/blobs/${digest}") ?: 0
        }
      }
      [name: tag, data: manifest.json, size: size, exists: exists]
    }
    tags
  }

  def tag() {
    def name = URLDecoder.decode(params.name, 'UTF-8')
    def res = restService.get("${name}/manifests/${params.id}").json
    def history = res.history.v1Compatibility.collect { jsonValue ->
      def json = new JsonSlurper().parseText(jsonValue)
      //log.info json as JSON
      json
    }
    [history: history]
  }

  def delete() {
    def name = params.name
    def tag = params.id
    def manifest = restService.get("${name}/manifests/${tag}")
    def digest = manifest.responseEntity.headers.getFirst('Docker-Content-Digest')
    log.info digest
    /*
    def blobSums = manifest.json.fsLayers?.blobSum
    blobSums.each { digest ->
      log.info "Deleting blob: ${digest}"
      restService.delete("${name}/blobs/${digest}")
    }
    */
    log.info "Deleting manifest"
    restService.delete("${name}/manifests/${digest}")

    redirect action: 'tags', id: name
  }
}
