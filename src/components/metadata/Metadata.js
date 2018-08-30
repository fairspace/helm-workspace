import React from 'react';
import MetadataViewer from "./MetadataViewer";
import ErrorDialog from "../error/ErrorDialog";

class Metadata extends React.Component {
    constructor(props) {
        super(props);
        this.subject = props.subject;
        this.metadataStore = props.metadataStore;

        this.state = {
            loading: false,
            error: false,
            errorMessage: null,
            vocabulary: {},
            metadata: {}
        };
    }

    componentWillMount() {
        this.loadData();
    }

    loadData() {
        if(!this.subject) {
            const errorMessage =  "No subject given to retrieve metadata for";
            this.setState({error: true});
            ErrorDialog.showError("No subject provided", errorMessage);
            return;
        }

        this.setState({loading: true, error: false});

        Promise.all([
            this.metadataStore.getVocabulary(),
            this.metadataStore.get(this.subject)
        ]).then(([vocabulary, metadata]) => {
            if(this.willUnmount) return;

            this.setState({loading: false, vocabulary: vocabulary, metadata: metadata})
        }).catch(e => {
            if(this.willUnmount) return;
            const errorMessage = "Error while loading metadata";
            this.setState({error: true});
            ErrorDialog.showError(e, errorMessage);
        })
    }

    componentWillUnmount() {
        this.willUnmount = true
    }

    componentWillReceiveProps(props) {
        this.subject = props.subject;
        this.loadData();
    }

    render() {
        if(this.state.error) {
            return (<div>An error has occured</div>)
        } else if(this.state.loading) {
            return (<div>Loading...</div>)
        } else {
            return (<MetadataViewer vocabulary={this.state.vocabulary} metadata={this.state.metadata}/>)
        }
    }

}

export default Metadata
