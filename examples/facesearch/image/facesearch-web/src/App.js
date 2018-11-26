import React, { Component } from 'react';
import './App.css';

import { Provider } from 'react-redux';
import { createStore, applyMiddleware } from 'redux';
import { combineReducers } from 'redux'
import thunk from 'redux-thunk';

const thing_reducer = (state = [], action) => {
  console.log(`list_reducer current: ${JSON.stringify(state)} action: ${JSON.stringify(action)}`)

  return state
}

const root_reducer = combineReducers({
  things: thing_reducer,
})

class App extends Component {

  constructor(props) {
    super(props);
    this.state = {
      store: createStore(
        root_reducer,
        applyMiddleware(thunk),
      ),
    };
    this.connect()
  }

  connect() {
    this.ws = new WebSocket('ws://127.0.0.1:8080/ws/updates');
    this.ws.onmessage = evt => {
      // When we receive an update from the ws, we want to dispatch this
      // to the state store
      //
      const updates = JSON.parse(evt.data)
      if (updates instanceof Array) {
        // A list of events to dispatch
        //
        updates.each((update) => {
          this.state.store.dispatch(update)
        })
      }
      else {
        // A single event
        //
        this.state.store.dispatch(updates)
      }
    }

    this.ws.onclose =
      (err) => {
        console.log(`Socket closed. Reconnect in 5 seconds. (${err.message})`)
        setTimeout(() => {
        this.connect()
      },
      5000)
    }

    this.ws.onerror = (err) => {
      console.error(`Socket error, closed. (${err.message})`)
      this.ws.close()
    }

    // Get the list as we connect
    //
    this.ws.onopen = () => {
      // Do whatever
    }
  }

  componentWillMount() {
    // Notify react to update when the state store updates
    //
    this.state.store.subscribe(() => {
      console.log("Store subscribe()")
      this.forceUpdate();
    });
  }

  refresh() {
    this.ws.send(JSON.stringify({
      type: "LIST",
      cmd: "REFRESH"
    }));
    this.ws.send(JSON.stringify({
      type: "ITEM",
      cmd: "REFRESH"
    }));
  }

  render() {
    return (
      <Provider store={this.state.store}>
        <div className="App">
          <header className="App-header">
            <h1>Facesearch</h1>
          </header>
          <div className="App-body">
            <a href="#" className="App-link" onClick={ this.refresh.bind(this) }>[refresh]</a>
          </div>
        </div>
      </Provider>
    );
  }
}

export default App;
