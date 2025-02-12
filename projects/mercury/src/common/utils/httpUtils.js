/**
 * This method returns a function that handles HTTP error scenarios
 *      In case of a HTTP status 401, the user will be redirected to the login page
 *      In case of any other response, a promise rejection with the reason of the error is returned.
 *
 * @param providedMessage   If the backend does not provide an error message, this message will be given in the Error
 * @returns {Function}
 */
import axios, {AxiosError} from 'axios';
import ErrorDialog from '../components/ErrorDialog';

export const handleAuthError = status => {
    switch (status) {
        case 401:
            sessionStorage.clear();
            window.location.assign(`/login?redirectUrl=${encodeURIComponent(window.location.href)}`);
            break;
        case 403:
            ErrorDialog.showError(
                'You have no access to this resource. Ask your administrator to grant you access.',
                null,
                () => window.location.assign('/workspaces')
            );
            break;
        default:
    }
};

export function handleHttpError(defaultMessage) {
    return (e: Error | AxiosError) => {
        if (e && axios.isCancel(e)) {
            return;
        }
        if (!e || !e.response) {
            throw Error(defaultMessage);
        }
        const {
            response: {status, data}
        } = e;

        switch (status) {
            case 401:
            case 403:
                handleAuthError(status);
                break;
            default:
                if (status === 400 && data) {
                    if (typeof data === 'string') {
                        throw Error(data);
                    }
                    throw data;
                }
                throw Error(defaultMessage);
        }
    };
}

export function handleRemoteSourceHttpError(defaultMessage) {
    return (e: Error | AxiosError) => {
        if (e && axios.isCancel(e)) {
            return;
        }
        if (!e || !e.response) {
            throw Error(defaultMessage);
        }
        const {
            response: {status, data}
        } = e;
        if (status === 400 && data) {
            if (typeof data === 'string') {
                throw Error(data);
            }
            throw data;
        }
        throw Error(defaultMessage);
    };
}

/**
 * This function will extract the data property of the axios response if the content-type in the headers contains 'json'
 * otherwise it will throw an error
 * @param response
 */
export function extractJsonData(response) {
    if (!response) {
        throw Error('Cannot parse empty response');
    }
    const {headers, data} = response;
    const contentType = headers ? headers['content-type'] : '';
    const isJson = contentType && contentType.includes('json');

    if (isJson) {
        return data;
    }

    throw Error(`Unable to parse response${contentType ? ', content type: ' + contentType : ''}`);
}
