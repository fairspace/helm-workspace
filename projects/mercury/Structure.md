# Mercury project structure

The project follows the [feature based approach](https://reactjs.org/docs/faq-structure.html).

## Features

The feature folders (`collections`, `file`, `metadata`, `users`, `workspaces`) contain:

-   \*API.js
    -   Service file to communicate with the backend.
-   \*Page
    -   The main entry point for the feature/route
-   Other specialized components specific to the feature

## Shared components and functions

-   `/common` (Shared code)
    -   `/components`
        -   [CollectionInformationDrawer](src/collections/CollectionInformationDrawer.js) (Side panel contains collection info, collaborators/permissions and metadata for selected collection, directories or files)
    -   `/context` (shared/injected state/logic) [Read on React Context](https://reactjs.org/docs/context.html)
    -   `/hooks` (pure logic/state no UI) [Read on React Hooks](https://reactjs.org/docs/hooks-intro.html)
    -   [/services/Config.js](./src/common/services/Config.js) (configuration from backend)
    -   `/utils` (Utility files, pure functions)
-   `/metadata` (could be renamed to linkeddata)
    -   Generic components (for metadata and vocabulary) are called LinkedData\*.js
    -   `/common` (shared components between Vocabulary and Metadata)
        -   values (the different form fields, text input, dates, dropdown etc.)
        -   [LinkedDataEntityForm](./src/metadata/common/LinkedDataEntityForm.js) This component and its child components are probably the most complex areas in the frontend code. Some refactoring might be required in case there is any expected changes and in order to make the code easier to maintain.
    -   [LinkedDataContext](./src/metadata/LinkedDataContext.js) with a provider [LinkedDataMetadataProvider](./src/metadata/LinkedDataMetadataProvider.js)
    -   [LinkedDataWrapper](./src/metadata/LinkedDataWrapper.js)
        -   2 Wrappers to provide needed values to either metadata or vocabulary
    -   [UseLinkedData](src/metadata/common/UseLinkedData.js)
        -   Fetches the entity whether it's part of the metadata or vocabulary, using the provided subject
    -   [UseFormData](src/metadata/common/UseFormData.js)
        -   Contains the currently loaded form/entity values, changes, validation state and form operations.
    -   [UseFormSubmission](src/metadata/common/UseFormSubmission.js)
        -   Submits form changes and handles submission errors
-   `/permissions`

## Metadata forms

The main models used to render forms are properties which are the blueprints of each field, they are plain objects that
are built using generatePropertyEntry from [vocabularyUtils](src/metadata/common/vocabularyUtils.js). Properties have
some generic attributes such as label, description and datatype, plus some helping attributes that are defined by the '
shape' of the linked data entity, such as minValuesCount, order and machineOnly etc.  
The values are objects in which each object links to the property by the IRI of the property and the value of the
key/IRI is the list of values for this property.

The forms components can be broken down as:

-   [LinkedDataEntityFormContainer](./src/metadata/common/LinkedDataEntityFormContainer.js)
    -   Applies some specific logic according to the type of linked data (metadata or vocabulary) using [LinkedDataContext](./src/metadata/LinkedDataContext.js)
    -   This component fetches the linked data entity
    -   Keeps form values, changes and validations using useFormData
    -   Submits changes using useFormSubmission
    -   Supplies properties and callback functions to [LinkedDataEntityForm](./src/metadata/common/LinkedDataEntityForm.js)
-   [LinkedDataEntityForm](./src/metadata/common/LinkedDataEntityForm.js)
    -   The main form view that provides each property and delegates actions to [LinkedDataProperty](./src/metadata/common/LinkedDataProperty.js)
-   [LinkedDataProperty](./src/metadata/common/LinkedDataProperty.js)
    -   This is the one that is concerned with rendering each field. It uses the [LinkedDataValueComponentFactory](./src/metadata/common/values/LinkedDataValueComponentFactory.js) which is coming from [LinkedDataContext](./src/metadata/LinkedDataContext.js) to pick the right edit and add component of the field. The [LinkedDataValueComponentFactory](./src/metadata/common/values/LinkedDataValueComponentFactory.js) mainly uses the property datatype (plus a few attributes such as isRdfList) to the proper 'value' input component. There is a big list of value components, such as, [StringValue](./src/metadata/common/values/StringValue.js), [NumberValue](./src/metadata/common/values/NumberValue.js) and [SwitchValue](./src/metadata/common/values/SwitchValue.js) etc. that are found in the [/metadata/common/values](src/metadata/common/values) directory. Then depending on the property whether it's a relation shape (Relation shapes are reference values to other entities) or not the component will render either [LinkedDataRelationTable](./src/metadata/common/LinkedDataRelationTable.js) or [LinkedDataInputFieldsTable](./src/metadata/common/LinkedDataInputFieldsTable.js).

## Keep in mind

Not all components/files are covered, mainly the important ones and the need description.
