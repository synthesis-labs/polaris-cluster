import React, { Component } from 'react';
import './App.css';

import { Provider } from 'react-redux';
import { createStore, applyMiddleware } from 'redux';
import { combineReducers } from 'redux'
import thunk from 'redux-thunk';

import List from './List'

const list_reducer = (state = [], action) => {
  console.log(`list_reducer current: ${JSON.stringify(state)} action: ${JSON.stringify(action)}`)
  if (action.type != "LIST")
    return state
  else {
    switch (action.action) {
      case "ACTIVE": {
        return state
            .filter((l) => { return l.name != action.data.name })
            .concat(action.data)
        break
      }
      case "DELETED": {
        return state
            .filter((l) => { return l.name != action.data.name })
        break
      }
      default:
        console.log("Some other state not being handled?")
        return state
    }
  }
}

const item_reducer = (state = {}, action) => {
  console.log(`item_reducer current: ${JSON.stringify(state)} action: ${JSON.stringify(action)}`)
  if (action.type != "ITEM")
    return state
  else {
    switch (action.action) {
      case "ACTIVE": {
        let listitems = state[action.data.list] || []
        listitems = listitems
            .filter((i) => { return i.name != action.data.name })
            .concat(action.data)
        state[action.data.list] = listitems
        return state
        break
      }
      case "DELETED": {
        let listitems = state[action.data.list] || []
        listitems = listitems
            .filter((i) => { return i.name != action.data.name })
        state[action.data.list] = listitems
        return state
        break
      }
      default:
        console.log("Some other state not being handled?")
        return state
    }
  }
}

const root_reducer = combineReducers({
  lists: list_reducer,
  items: item_reducer,
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
    const wsaddr = ((window.location.protocol === "https:") ? "wss://" : "ws://") + window.location.host + "/ws/updates"
    this.ws = new WebSocket(wsaddr);
    this.ws.onmessage = evt => {
      // When we receive an update from the ws, we want to dispatch this
      // to the state store
      //
      const updates = JSON.parse(evt.data)
      if (updates instanceof Array) {
        // A list of events to dispatch
        //
        updates.map((update) => {
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
      this.refresh()
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

  new_list() {
    const cmd = {
      type: "LIST",
      cmd: "CREATE",
      data: {
        name: this.refs.new_list_name.value
      }
    };

    this.ws.send(JSON.stringify(cmd));
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
            <h1>Hyper-scale Todo App</h1>
          </header>
          <div className="App-body">
            <div>
              {
                this.state.store.getState().lists.map((list) =>
                  <List key={list.name} {...list} ws={this.ws} store={this.state.store}></List>
                )
              }
            </div>
            New list ==>&nbsp;
            <input type="text"
              ref="new_list_name"
            />
            &nbsp;<a href="#" className="App-link" onClick={ this.new_list.bind(this) }>[+]</a>
            &nbsp;<a href="#" className="App-link" onClick={ this.refresh.bind(this) }>[refresh]</a>
          </div>
        </div>
      </Provider>
    );
  }
}

export default App;
