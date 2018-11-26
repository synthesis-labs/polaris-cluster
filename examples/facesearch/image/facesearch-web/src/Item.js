import React, { Component } from 'react';
import PropTypes from 'prop-types';

class Item extends Component {
  constructor(props) {
    super(props);
  }

  deleteMe() {
    const cmd = {
      type: "ITEM",
      cmd: "DELETE",
      data: {
        name: this.props.name,
        list: this.props.list,
      }
    };

    this.props.ws.send(JSON.stringify(cmd));
  }

  render() {
    return (
      <div className="Item">
        { this.props.name }
        &nbsp;
        <a href="#" className="App-link" onClick={this.deleteMe.bind(this)}>[X]</a>
      </div>
    )
  }
};

Item.propTypes = {
  name: PropTypes.string.isRequired,
  list: PropTypes.string.isRequired,
  ws: PropTypes.object.isRequired,
}

export default Item;
