import React, { Component } from 'react';
import PropTypes from 'prop-types';
import Item from './Item'

class List extends Component {
  constructor(props) {
    super(props);
  }

  deleteMe() {
    const cmd = {
      type: "LIST",
      cmd: "DELETE",
      data: {
        name: this.props.name
      }
    };

    this.props.ws.send(JSON.stringify(cmd));
  }

  new_item() {
    const cmd = {
      type: "ITEM",
      cmd: "CREATE",
      data: {
        name: this.refs.new_item_name.value,
        list: this.props.name,
      }
    };

    this.props.ws.send(JSON.stringify(cmd));
  }

  render() {
    let items = this.props.store.getState().items[this.props.name] || []
    return (
      <div className="List">
        <h2>
          {this.props.name}
          &nbsp;
          <a onClick={this.deleteMe.bind(this)} href="#" className="App-link">[x]</a>
        </h2>
        {
          items.map((item) => {
            return <Item key={item.name} {...item} ws={this.props.ws}/>
          })
        }
        <br/>
        New item ==>&nbsp;
        <input type="text"
          ref="new_item_name"
        />
        &nbsp;
        <a href="#" className="App-link" onClick={this.new_item.bind(this)}>[+]</a>
        <hr/>
      </div>
    )
  }
};

List.propTypes = {
  name: PropTypes.string.isRequired,
  ws: PropTypes.object.isRequired,
  store: PropTypes.object.isRequired,
}

export default List;
