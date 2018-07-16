package io.fairspace.ceres

import com.typesafe.config.ConfigFactory
import io.fairspace.ceres.repository.ModelRepository
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import org.apache.jena.query.DatasetFactory
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFFormat
import org.apache.jena.vocabulary.VCARD
import org.koin.dsl.module.Module
import org.koin.dsl.module.applicationContext
import org.koin.standalone.StandAloneContext
import org.koin.test.KoinTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

open class BaseCeresTest : KoinTest {
    private val context = applicationContext {
        bean { DatasetFactory.create() }
        bean { ModelRepository(get()) }
        bean { HoconApplicationConfig(ConfigFactory.load()) as ApplicationConfig }
    }

    val personURI = "http://somewhere/JohnSmith"
    val givenName = "John"
    val familyName = "Smith"
    val fullName = "$givenName $familyName"

    val model = ModelFactory.createDefaultModel().apply {
        createResource(personURI)
                .addProperty(VCARD.FN, fullName)
                .addProperty(VCARD.N,
                        createResource()
                                .addProperty(VCARD.Given, givenName)
                                .addProperty(VCARD.Family, familyName))
    }

    val RDF_JSON = RDFFormat.RDFJSON.lang.headerString

    @BeforeTest
    fun before(){
        StandAloneContext.startKoin(koinModules())
    }

    @AfterTest
    fun after(){
        StandAloneContext.closeKoin()
    }

    open fun koinModules(): List<Module> = listOf(context)
}