#
# Module to initialise the EHRI environment
#

require "#{File.dirname(__FILE__)}/ehriutils"

module Ehri

  EhriUtils::check_env

  # The magic necessary to do Java stuff...
  require "java"

  # Import Java classes like so...
  java_import "com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph"
  java_import "com.tinkerpop.frames.FramedGraphFactory"
  java_import "com.tinkerpop.frames.modules.gremlingroovy.GremlinGroovyModule"
  java_import "com.tinkerpop.frames.modules.javahandler.JavaHandlerModule"
  java_import "eu.ehri.project.core.GraphManagerFactory"
  java_import "eu.ehri.project.models.EntityClass"
  java_import "eu.ehri.project.models.base.Frame"
  java_import "eu.ehri.project.definitions.EventTypes"
  java_import "com.google.common.base.Optional"

  # Define most stuff within constant packages
  module Core
    include_package "eu.ehri.project.core"
  end

  module Importers
    include_package "eu.ehri.project.importers"
  end

  module Persistence
    include_package "eu.ehri.project.persistence"
  end

  module Models
    include_package "eu.ehri.project.models"
  end

  module Acl
    include_package "eu.ehri.project.acl"
  end

  module Commands
    include_package "eu.ehri.project.commands"
  end

  module Views
    include_package "eu.ehri.project.views"
  end

  module Exceptions
    include_package "eu.ehri.project.exceptions"
  end

  DB_PATH = ENV['NEO4J_DB'] ||= "#{ENV['NEO4J_HOME']}/data/graph.db"

  # Initialise a graph and the manager.
  # Note: the graph must not be being used elsewhere (i.e. by the server)
  #
  
  class CheckTransactionGraph < Neo4jGraph
    def check_transaction
      if not @tx.nil? and not @tx.get.nil?
        raise "Graph is already in a transaction!"
      end
    end
  end

  Graph = FramedGraphFactory.new(JavaHandlerModule.new, GremlinGroovyModule.new).create(CheckTransactionGraph.new DB_PATH)
  Manager = GraphManagerFactory.get_instance Graph

end
